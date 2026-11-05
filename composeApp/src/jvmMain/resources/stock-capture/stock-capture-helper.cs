// Local-only Windows stock recorder. No network, keyboard injection or screen-wide capture.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Automation;
using System.Windows.Forms;

namespace Aita.StockCapture {
    public static class Helper {
        internal const int MaxWords = 2000, MaxNodes = 5000, MaxImagePixels = 6000000;
        internal static readonly object OutputLock = new object();
        internal static readonly JavaScriptSerializer Json = new JavaScriptSerializer { MaxJsonLength = 12000000 };
        internal static int OwnerPid;
        internal static bool Closing;
        internal static void Emit(object value) {
            lock (OutputLock) { if (!Closing) { Console.Out.WriteLine(Json.Serialize(value)); Console.Out.Flush(); } }
        }
        [DllImport("user32.dll")] internal static extern bool EnumWindows(EnumWindowsProc callback, IntPtr extra);
        internal delegate bool EnumWindowsProc(IntPtr h, IntPtr extra);
        [DllImport("user32.dll")] internal static extern bool IsWindowVisible(IntPtr h);
        [DllImport("user32.dll")] internal static extern bool IsWindow(IntPtr h);
        [DllImport("user32.dll")] internal static extern bool IsIconic(IntPtr h);
        [DllImport("user32.dll")] internal static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll", CharSet=CharSet.Unicode)] internal static extern int GetWindowText(IntPtr h, StringBuilder b, int max);
        [DllImport("user32.dll")] internal static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
        [DllImport("user32.dll")] internal static extern bool GetClientRect(IntPtr h, out RECT r);
        [DllImport("user32.dll")] internal static extern bool ClientToScreen(IntPtr h, ref POINT p);
        [DllImport("user32.dll")] internal static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
        [DllImport("user32.dll")] internal static extern bool SetProcessDPIAware();
        [StructLayout(LayoutKind.Sequential)] internal struct RECT { public int Left,Top,Right,Bottom; }
        [StructLayout(LayoutKind.Sequential)] internal struct POINT { public int X,Y; }
        internal static string Title(IntPtr h) { var b=new StringBuilder(256); GetWindowText(h,b,b.Capacity); return b.ToString(); }
        internal static uint Pid(IntPtr h) { uint p; GetWindowThreadProcessId(h,out p); return p; }
        internal static bool AllowedWindow(IntPtr h) {
            return IsWindow(h) && IsWindowVisible(h) && Pid(h)!=OwnerPid && Pid(h)!=Process.GetCurrentProcess().Id;
        }
        public static void Run(string mode, string id, string language, int ownerPid, int expectedPid) {
            OwnerPid=ownerPid; SetProcessDPIAware();
            if(mode=="Verify") { Emit(new {type="verified",protocol=1}); return; }
            if(mode=="List") {
                var windows=new List<object>();
                EnumWindows(delegate(IntPtr h,IntPtr p) {
                    if(windows.Count<200 && AllowedWindow(h) && !IsIconic(h)) {
                        string t=Title(h); RECT r;
                        if(t.Length>0 && GetClientRect(h,out r) && r.Right>100 && r.Bottom>60)
                            windows.Add(new {id=h.ToInt64().ToString(),title=t,pid=Pid(h)});
                    } return true;
                },IntPtr.Zero);
                Emit(new {type="windows",windows=windows}); return;
            }
            long handle;
            if(!long.TryParse(id,out handle) || handle<=0 || expectedPid<=0 || Pid(new IntPtr(handle))!=(uint)expectedPid || !AllowedWindow(new IntPtr(handle))) {
                Emit(new {type="state",status="ERROR",code="source_unavailable"}); return;
            }
            Application.EnableVisualStyles();
            var overlay=new RecorderOverlay(new IntPtr(handle),language);
            var input=new Thread(delegate() {
                try {
                    string line;
                    while((line=Console.In.ReadLine())!=null) {
                        if(line.Length>40) break;
                        var command=line;
                        if(overlay.IsHandleCreated && !overlay.IsDisposed)
                            overlay.BeginInvoke((Action)delegate { overlay.Command(command); });
                    }
                } catch { }
                if(overlay.IsHandleCreated && !overlay.IsDisposed)
                    try { overlay.BeginInvoke((Action)delegate { overlay.Stop(null); }); } catch { }
            });
            input.IsBackground=true;
            overlay.Shown+=delegate { input.Start(); };
            Application.Run(overlay);
            Closing=true;
            // A broken third-party UI Automation provider must not keep the helper alive.
            Environment.Exit(0);
        }
        internal sealed class Word {
            public string text; public float x,y,width,height,confidence=1;
        }
        internal static List<Word> ReadAccessible(IntPtr window, POINT origin, int width, int height, Func<bool> continueReading=null) {
            var words=new List<Word>();
            var seen=new HashSet<string>();
            var walk=TreeWalker.ControlViewWalker;
            var root=AutomationElement.FromHandle(window);
            var queue=new Queue<AutomationElement>();
            queue.Enqueue(root);
            var watch=Stopwatch.StartNew();
            int visited=0;
            while(queue.Count>0 && visited++<MaxNodes && words.Count<MaxWords && watch.ElapsedMilliseconds<1300 && GetForegroundWindow()==window && (continueReading==null||continueReading())) {
                var item=queue.Dequeue();
                try {
                    var current=item.Current;
                    // A password control and its entire subtree are excluded.
                    if(current.IsPassword || current.IsOffscreen) continue;
                    var bounds=current.BoundingRectangle;
                    if(bounds.IsEmpty || bounds.Width<=0 || bounds.Height<=0) continue;
                    double x=bounds.X-origin.X,y=bounds.Y-origin.Y;
                    var child=walk.GetFirstChild(item);
                    if(child!=null) {
                        int siblings=0;
                        while(child!=null && queue.Count<MaxNodes && siblings++<MaxNodes && watch.ElapsedMilliseconds<1300) {
                            queue.Enqueue(child); child=walk.GetNextSibling(child);
                        }
                        continue;
                    }
                    // Keep full visible cells only. Clamping a clipped cell's rectangle while
                    // retaining its complete accessible Value would import unseen text.
                    if(!FullyVisibleBounds(x,y,bounds.Width,bounds.Height,width,height)) continue;
                    var ct=current.ControlType;
                    if(ct!=ControlType.Text && ct!=ControlType.Edit && ct!=ControlType.DataItem && ct!=ControlType.ListItem && ct!=ControlType.Document && ct!=ControlType.HeaderItem && ct!=ControlType.Custom) continue;
                    string text=current.Name ?? "";
                    object value;
                    bool hasValue=item.TryGetCurrentPattern(ValuePattern.Pattern,out value);
                    if(ct==ControlType.Custom && !hasValue) continue;
                    // Grid cell Name often describes its column/row, while Value contains stock.
                    // Read-only DataItem cells expose this pattern too, not just Edit controls.
                    if(ct!=ControlType.HeaderItem && hasValue)
                        text=((ValuePattern)value).Current.Value ?? text;
                    text=text.Trim();
                    if(text.Length==0 || text.Length>512 || x<0 || y<0) continue;
                    string key=text+"|"+Math.Round(x)+"|"+Math.Round(y);
                    if(seen.Add(key)) words.Add(new Word {text=text,x=(float)x,y=(float)y,width=(float)bounds.Width,height=(float)bounds.Height});
                } catch(ElementNotAvailableException) { } catch(InvalidOperationException) { }
            }
            return words;
        }
        internal static string CapturePng(IntPtr window,int width,int height) {
            // PrintWindow renders THIS HWND, including while another window overlaps it.
            // Never substitute CopyFromScreen/desktop capture on failure.
            using(var bitmap=new Bitmap(width,height,PixelFormat.Format24bppRgb)) {
                using(var graphics=Graphics.FromImage(bitmap)) {
                    graphics.Clear(Color.White);
                    IntPtr dc=graphics.GetHdc(); bool ok;
                    try { ok=PrintWindow(window,dc,3); } finally { graphics.ReleaseHdc(dc); }
                    if(!ok) throw new InvalidOperationException();
                }
                using(var stream=new MemoryStream()) {
                    bitmap.Save(stream,ImageFormat.Png);
                    if(stream.Length>7000000) throw new InvalidOperationException();
                    return Convert.ToBase64String(stream.ToArray());
                }
            }
        }
        internal static bool FullyVisibleBounds(double x,double y,double width,double height,int clientWidth,int clientHeight) {
            return x>=0 && y>=0 && width>0 && height>0 && x+width<=clientWidth && y+height<=clientHeight;
        }
        internal static bool NeedsOcr(int visibleWordCount,int sampleNumber) {
            // Numeric toolbar/account/phone text does not prove a grid exposes its cells.
            // Periodic OCR must remain reachable regardless of accessibility content.
            return visibleWordCount<8 || sampleNumber%5==0;
        }

    }
    internal sealed class RecorderOverlay:Form {
        readonly IntPtr source; readonly uint sourcePid;
        readonly Label caption=new Label(); readonly Button toggle=new Button(),stop=new Button();
        readonly System.Windows.Forms.Timer timer=new System.Windows.Forms.Timer();
        readonly string[] labels;
        readonly Action<object> emit;
        volatile bool recording; bool started,busy,finished; volatile int generation; int frames,ticks;
        DateTime busySince; Point dragPoint; string lastAccessibleFingerprint,lastOcrFingerprint;
        protected override bool ShowWithoutActivation { get { return true; } }
        protected override CreateParams CreateParams { get {var p=base.CreateParams;p.ExStyle|=0x08000000|0x80;return p;} }
        protected override void WndProc(ref Message m) { if(m.Msg==0x21){m.Result=new IntPtr(3);return;}base.WndProc(ref m); }
        internal RecorderOverlay(IntPtr window,string language,Action<object> observer=null) {
            emit=observer ?? new Action<object>(Helper.Emit);
            source=window;sourcePid=Helper.Pid(window);labels=Labels(language);
            Text="AITA";TopMost=true;ShowInTaskbar=false;FormBorderStyle=FormBorderStyle.None;
            BackColor=Color.FromArgb(24,28,37);ForeColor=Color.White;ClientSize=new Size(366,66);
            StartPosition=FormStartPosition.Manual;
            var area=Screen.FromHandle(window).WorkingArea;Location=new Point(area.Right-Width-20,area.Top+20);
            Font=new Font("Segoe UI",9);
            caption.Text="AITA · "+labels[0];caption.SetBounds(12,8,220,20);caption.ForeColor=Color.White;
            caption.MouseDown+=delegate(object s,MouseEventArgs e){if(e.Button==MouseButtons.Left)dragPoint=e.Location;};
            caption.MouseMove+=delegate(object s,MouseEventArgs e){if(e.Button==MouseButtons.Left)Location=new Point(Left+e.X-dragPoint.X,Top+e.Y-dragPoint.Y);};
            toggle.Text=labels[1];toggle.SetBounds(12,33,240,25);toggle.BackColor=Color.FromArgb(255,191,45);toggle.ForeColor=Color.Black;toggle.FlatStyle=FlatStyle.Flat;toggle.FlatAppearance.BorderSize=0;toggle.TabStop=false;
            stop.Text=labels[4];stop.SetBounds(262,33,92,25);stop.BackColor=Color.FromArgb(65,72,86);stop.ForeColor=Color.White;stop.FlatStyle=FlatStyle.Flat;stop.FlatAppearance.BorderSize=0;stop.TabStop=false;
            Controls.Add(caption);Controls.Add(toggle);Controls.Add(stop);
            toggle.Click+=delegate{if(recording)Pause("paused");else Resume();};stop.Click+=delegate{Stop(null);};
            FormClosing+=delegate{finished=true;generation++;timer.Stop();};
            Shown+=delegate{State("PAUSED","ready");};
            timer.Interval=1000;timer.Tick+=delegate{Tick();};timer.Start();
        }
        static string[] Labels(string language) {
            switch(language.ToLowerInvariant().Split('-')[0]) {
                case "ru":return new[]{"Импорт склада","Начать запись","Пауза","Продолжить","Стоп","Запись","Пауза"};
                case "kk":return new[]{"Қойманы импорттау","Жазуды бастау","Үзіліс","Жалғастыру","Тоқтату","Жазылуда","Үзіліс"};
                case "ky":return new[]{"Кампаны импорттоо","Жазууну баштоо","Тыныгуу","Улантуу","Токтотуу","Жазылууда","Тыныгуу"};
                case "tg":return new[]{"Воридоти анбор","Оғози сабт","Таваққуф","Идома","Анҷом","Сабт","Таваққуф"};
                case "uz":return new[]{"Omborni import qilish","Yozishni boshlash","Pauza","Davom etish","To‘xtatish","Yozilmoqda","Pauza"};
                default:return new[]{"Stock import","Start recording","Pause","Resume","Stop","Recording","Paused"};
            }
        }
        void State(string status,string code) {
            emit(new {type="state",status=status,code=code,frames=frames});
            caption.Text="AITA · "+(recording?labels[5]:labels[6])+" · "+frames;
            toggle.Text=recording?labels[2]:(started?labels[3]:labels[1]);
        }
        internal void Command(string command) {
            if(command=="pause")Pause("paused");else if(command=="resume")Resume();else if(command=="stop")Stop(null);
            else if(command=="ack")busy=false;
        }
        void Pause(string code) {if(finished)return;recording=false;generation++;State("PAUSED",code);}
        void Resume() {
            if(finished)return;
            if(Helper.GetForegroundWindow()!=source){Pause("source_not_foreground");return;}
            started=true;recording=true;generation++;State("RECORDING",null);Tick();
        }
        internal void Stop(string code) {
            if(finished)return;recording=false;generation++;State(code==null?"STOPPED":"ERROR",code);finished=true;timer.Stop();Close();
        }
        void Tick() {
            if(finished)return;
            if(!Helper.IsWindow(source)||Helper.Pid(source)!=sourcePid){Stop("source_closed");return;}
            if(Helper.IsIconic(source)){Stop("source_minimized");return;}
            if(!Helper.IsWindowVisible(source)){Stop("source_unavailable");return;}
            if(recording && Helper.GetForegroundWindow()!=source){Pause("source_not_foreground");return;}
            emit(new {type="heartbeat"});
            if(busy && (DateTime.UtcNow-busySince).TotalSeconds>22){Stop("source_unresponsive");return;}
            if(!recording || busy)return;
            busy=true;busySince=DateTime.UtcNow;int currentGeneration=generation;int sequence=++ticks;
            var thread=new Thread(delegate() {
                try {
                    Helper.RECT rect;var origin=new Helper.POINT();
                    if(!Helper.GetClientRect(source,out rect)||!Helper.ClientToScreen(source,ref origin))throw new InvalidOperationException();
                    int w=rect.Right,h=rect.Bottom;
                    if(w<10||h<10||(long)w*h>Helper.MaxImagePixels) {
                        BeginInvoke((Action)delegate{Stop("source_size_unsupported");});return;
                    }
                    var words=Helper.ReadAccessible(source,origin,w,h,delegate{return recording&&generation==currentGeneration;});
                    // Verify custom-painted rows every fifth sampled view, even if accessible
                    // toolbar text contains barcode-like account or telephone numbers.
                    // Identical images are deduplicated before invoking the OCR worker.
                    bool ocr=Helper.NeedsOcr(words.Count,sequence);
                    if(!recording||generation!=currentGeneration) { BeginInvoke((Action)delegate{busy=false;}); return; }
                    string png=ocr?Helper.CapturePng(source,w,h):null;
                    string fingerprint;
                    using(var hash=SHA256.Create()) {
                        string content;
                        if(ocr) content=png;
                        else {var text=new StringBuilder();foreach(var word in words)text.Append(word.text).Append('|').Append(word.x).Append('|').Append(word.y).Append(';');content=text.ToString();}
                        fingerprint=Convert.ToBase64String(hash.ComputeHash(Encoding.UTF8.GetBytes(content)))+"|"+w+"|"+h;
                    }
                    BeginInvoke((Action)delegate {
                        if(finished||!recording||generation!=currentGeneration||Helper.GetForegroundWindow()!=source){busy=false;return;}
                        if((ocr?lastOcrFingerprint:lastAccessibleFingerprint)==fingerprint){busy=false;return;}
                        if(ocr)lastOcrFingerprint=fingerprint;else lastAccessibleFingerprint=fingerprint;
                        frames++;
                        emit(new {type="frame",id=sequence.ToString(),at=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),width=w,height=h,source=ocr?"OCR":"UI_AUTOMATION",title=Helper.Title(source),words=ocr?new List<Helper.Word>():words,png=png});
                        caption.Text="AITA · "+labels[5]+" · "+frames;
                    });
                } catch { try {BeginInvoke((Action)delegate{Stop("source_unavailable");});}catch{} }
            });
            thread.IsBackground=true;thread.SetApartmentState(ApartmentState.MTA);thread.Start();
        }
    }
}
