// Synthetic local acceptance fixture: no customer account or customer screen content.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Drawing;
using System.IO;
using System.Threading;
using System.Windows.Automation;
using System.Windows.Forms;
namespace Aita.StockCapture {
    public static class SyntheticFixture {
        public static int Run(bool verify) {
            if(verify) {
                VerifyCapturePolicies();
                if(!InteractiveDesktopAvailable()) {
                    Helper.Emit(new {type="fixture_unsupported",code="noninteractive_desktop"});
                    return 2;
                }
            }
            Application.EnableVisualStyles(); Helper.SetProcessDPIAware();
            var form=new Form { Text="AITA synthetic stock fixture · NOT CUSTOMER DATA",Size=new Size(1100,700),StartPosition=FormStartPosition.CenterScreen };
            var heading=new Label {Text="Synthetic fixture · Milk / Молоко · Barcodes remain strings",Dock=DockStyle.Top,Height=28};
            var toggle=new CheckBox {Text="Custom-painted table (forces local image OCR)",Dock=DockStyle.Top,Height=30};
            var detail=new Button {Text="Expand synthetic item details",Dock=DockStyle.Bottom,Height=35};
            var details=new TextBox {Text="Молоко 2,5% | 4601234567893 | Quantity: 12,5 | Unit: kg | Category: Dairy",Dock=DockStyle.Bottom,Height=32,Visible=false};
            detail.Click+=delegate{details.Visible=!details.Visible;};
            var table=new DataGridView {Dock=DockStyle.Fill,ReadOnly=true,AllowUserToAddRows=false,RowHeadersVisible=false,AutoSizeColumnsMode=DataGridViewAutoSizeColumnsMode.Fill};
            foreach(var name in new[]{"Barcode / Штрихкод","Name / Название","Quantity / Количество","Unit / Ед.","Category / Категория","Supply price","Sale price"})table.Columns.Add(name,name);
            for(int i=0;i<100;i++)table.Rows.Add((4601234567893L+i).ToString(),i==0?"Молоко 2,5%":"Synthetic item "+i,"12,5",i%2==0?"kg":"g","Dairy","450,00","600,00");
            var canvas=new OcrCanvas {Dock=DockStyle.Fill,Visible=false};
            var scroll=new VScrollBar {Dock=DockStyle.Right,Minimum=0,Maximum=99,LargeChange=1,Visible=false};
            scroll.ValueChanged+=delegate{canvas.FirstRow=scroll.Value;canvas.Invalidate();};
            toggle.CheckedChanged+=delegate{table.Visible=!toggle.Checked;canvas.Visible=toggle.Checked;scroll.Visible=toggle.Checked;};
            form.Controls.Add(table);form.Controls.Add(canvas);form.Controls.Add(scroll);form.Controls.Add(details);form.Controls.Add(detail);form.Controls.Add(toggle);form.Controls.Add(heading);
            var clippedRight=new Label {Text="CLIPPED_RIGHT_FIXTURE",Bounds=new Rectangle(form.ClientSize.Width-12,100,180,22)};
            var clippedBottom=new Label {Text="CLIPPED_BOTTOM_FIXTURE",Bounds=new Rectangle(200,form.ClientSize.Height-8,240,24)};
            form.Controls.Add(clippedRight);form.Controls.Add(clippedBottom);clippedRight.BringToFront();clippedBottom.BringToFront();
            int result=0;
            var watchdog=new System.Windows.Forms.Timer {Interval=45000};
            if(verify) {
                watchdog.Tick+=delegate { result=1;watchdog.Stop();Helper.Emit(new {type="fixture_failed",code="probe_timeout"});form.Close(); };
                form.Shown+=delegate {
                    watchdog.Start();
                    var thread=new Thread(delegate() {
                        NativeProbe probe=null;
                        try {
                            Thread.Sleep(500);
                            if(Helper.GetForegroundWindow()!=form.Handle)throw new DesktopUnavailable();
                            probe=new NativeProbe(form,table,toggle);
                            probe.Run();
                            Helper.Emit(new {type="fixture_verified",uia=true,clipping=true,pauseResume=true,foregroundOwnership=true,
                                selectedWindowOcrBitmap=true,overlayNoActivate=true,stop=true});
                        } catch(DesktopUnavailable) {
                            result=2;Helper.Emit(new {type="fixture_unsupported",code="foreground_unavailable"});
                        } catch(Exception error) {
                            result=1;Helper.Emit(new {type="fixture_failed",code=error is ProbeFailure?error.Message:"native_probe_failed",
                                diagnostics=probe==null?null:probe.Diagnostics()});
                        } finally {
                            try {form.BeginInvoke((Action)delegate{watchdog.Stop();form.Close();});}catch{}
                        }
                    });thread.IsBackground=true;thread.SetApartmentState(ApartmentState.MTA);thread.Start();
                };
            }
            Application.Run(form);watchdog.Dispose();return result;
        }
        sealed class DesktopUnavailable:Exception { }
        sealed class ProbeFailure:Exception { internal ProbeFailure(string code):base(code){} }
        [DllImport("user32.dll")] static extern IntPtr OpenInputDesktop(uint flags,bool inherit,uint access);
        [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr handle);
        [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern bool GetUserObjectInformation(IntPtr handle,int index,StringBuilder value,int size,out int needed);
        static bool InteractiveDesktopAvailable() {
            if(!Environment.UserInteractive || Process.GetCurrentProcess().SessionId==0)return false;
            var desktop=OpenInputDesktop(0,false,1);
            if(desktop==IntPtr.Zero)return false;
            try {var name=new StringBuilder(256);int needed;return GetUserObjectInformation(desktop,2,name,512,out needed)&&name.ToString()=="Default";}
            finally{CloseDesktop(desktop);}
        }
        sealed class NativeProbe {
            readonly Form form;readonly DataGridView table;readonly CheckBox toggle;
            readonly object sync=new object();readonly IntPtr target;
            RecorderOverlay overlay;Form other;int frameCount,accessibleWords;string state,code,failure,lastSource;string[] lastWords=new string[0];string lastOcr;
            internal NativeProbe(Form form,DataGridView table,CheckBox toggle){this.form=form;this.table=table;this.toggle=toggle;target=form.Handle;}
            void Ui(Action action){if(form.IsDisposed)throw new ProbeFailure("probe_disposed");form.Invoke(action);}
            void Observe(object message) {
                // The observer sees only our generated HWND. Raw frames are not printed/saved.
                var data=Helper.Json.Deserialize<Dictionary<string,object>>(Helper.Json.Serialize(message));
                string type=Convert.ToString(data["type"]);
                lock(sync) {
                    if(type=="state") {state=Convert.ToString(data["status"]);code=Convert.ToString(data["code"]);}
                    if(type=="frame") {
                        if(Convert.ToString(data["title"])!=form.Text)failure="wrong_source";
                        var words=new List<string>();
                        foreach(object value in (System.Collections.IEnumerable)data["words"]) {
                            var word=(Dictionary<string,object>)value;string text=Convert.ToString(word["text"]);words.Add(text);
                            if(text.Contains("CLIPPED_")||text.Contains("OTHER_WINDOW_ONLY"))failure="unseen_text";
                        }
                        lastWords=words.ToArray();frameCount++;
                        lastSource=Convert.ToString(data["source"]);
                        accessibleWords=Convert.ToInt32(data["accessibleWords"]);
                        if(lastSource=="OCR")lastOcr=Convert.ToString(data["png"]);
                    }
                }
                if(type=="frame" && overlay!=null)overlay.Command("ack");
            }
            bool HasWord(string expected){lock(sync){return Array.Exists(lastWords,word=>word==expected);}}
            internal object Diagnostics(){lock(sync){
                var sample=new List<string>();foreach(string value in lastWords){if(sample.Count==12)break;sample.Add(value.Substring(0,Math.Min(100,value.Length)));}
                return new {state=state,code=code,frames=frameCount,source=lastSource,accessibleWords=accessibleWords,syntheticWords=sample,
                    syntheticAccessibility=InspectSyntheticAccessibility()};
            }}
            object InspectSyntheticAccessibility() {
                var result=new List<object>();var pending=new Stack<AutomationElement>();
                var watch=Stopwatch.StartNew();
                try {
                    var root=AutomationElement.FromHandle(target);pending.Push(root);
                    while(pending.Count>0&&result.Count<30&&watch.ElapsedMilliseconds<1000) {
                        var item=pending.Pop();var info=item.Current;object value;
                        bool valuePattern=item.TryGetCurrentPattern(ValuePattern.Pattern,out value);
                        var rectangle=info.BoundingRectangle;string name=info.Name??"";
                        result.Add(new {control=info.ControlType.ProgrammaticName,name=name.Substring(0,Math.Min(60,name.Length)),
                            offscreen=info.IsOffscreen,valuePattern=valuePattern,width=rectangle.Width,height=rectangle.Height});
                        if(item!=root) {var next=TreeWalker.ControlViewWalker.GetNextSibling(item);if(next!=null)pending.Push(next);}
                        var child=TreeWalker.ControlViewWalker.GetFirstChild(item);if(child!=null)pending.Push(child);
                    }
                }catch{}
                return result;
            }
            int Count(){lock(sync){return frameCount;}}
            void Wait(Func<bool> condition,string message,int milliseconds=8500) {
                var watch=Stopwatch.StartNew();
                while(watch.ElapsedMilliseconds<milliseconds) {
                    lock(sync){if(failure!=null)throw new ProbeFailure(failure);}
                    if(condition())return;Thread.Sleep(50);
                }
                throw new ProbeFailure(message);
            }
            internal void Run() {
                try {
                    Ui(delegate {overlay=new RecorderOverlay(target,"en",Observe);overlay.Show();});
                    IntPtr foreground=Helper.GetForegroundWindow();
                    if(foreground==overlay.Handle)throw new ProbeFailure("overlay_stole_focus");
                    if(foreground!=target)throw new DesktopUnavailable();
                    Ui(delegate{overlay.Command("resume");});
                    Wait(delegate{return HasWord("4601234567893");},"uia_frame_missing");
                    Ui(delegate{overlay.Command("pause");table.Rows[0].Cells[1].Value="Paused synthetic update";});
                    int paused=Count();Thread.Sleep(1600);
                    if(Count()!=paused)throw new ProbeFailure("captured_while_paused");
                    Ui(delegate{overlay.Command("resume");});
                    Wait(delegate{return HasWord("Paused synthetic update");},"resume_frame_missing");
                    Ui(delegate {
                        other=new Form {Text="AITA unrelated synthetic fixture",Size=new Size(420,160),StartPosition=FormStartPosition.CenterScreen};
                        other.Controls.Add(new Label {Text="OTHER_WINDOW_ONLY",Dock=DockStyle.Fill});other.Show();other.Activate();
                    });
                    if(Helper.GetForegroundWindow()!=other.Handle)throw new DesktopUnavailable();
                    Wait(delegate{lock(sync){return state=="PAUSED"&&code=="source_not_foreground";}},"foreground_pause_missing",3500);
                    int background=Count();
                    Ui(delegate{table.Rows[0].Cells[1].Value="Background source update";});Thread.Sleep(1600);
                    if(Count()!=background)throw new ProbeFailure("captured_in_background");
                    Ui(delegate{other.Close();form.Activate();});
                    if(Helper.GetForegroundWindow()!=target)throw new DesktopUnavailable();
                    Ui(delegate{overlay.Command("resume");});
                    Wait(delegate{return HasWord("Background source update");},"foreground_resume_missing");
                    Ui(delegate{toggle.Checked=true;});
                    Wait(delegate{lock(sync){return !String.IsNullOrEmpty(lastOcr);}},"ocr_capture_missing");
                    string png;lock(sync){png=lastOcr;}
                    using(var memory=new MemoryStream(Convert.FromBase64String(png)))using(var bitmap=new Bitmap(memory)) {
                        Helper.RECT rect;if(!Helper.GetClientRect(target,out rect)||bitmap.Width!=rect.Right||bitmap.Height!=rect.Bottom)throw new ProbeFailure("ocr_bounds");
                        bool varied=false;int first=bitmap.GetPixel(0,0).ToArgb();
                        for(int y=10;y<bitmap.Height&&!varied;y+=13)for(int x=10;x<bitmap.Width;x+=13)if(bitmap.GetPixel(x,y).ToArgb()!=first){varied=true;break;}
                        if(!varied)throw new ProbeFailure("ocr_bitmap_blank");
                    }
                    Ui(delegate{overlay.Stop(null);});int stopped=Count();Thread.Sleep(1100);
                    if(Count()!=stopped||!overlay.IsDisposed)throw new ProbeFailure("capture_did_not_stop");
                } finally {
                    try {Ui(delegate{if(overlay!=null&&!overlay.IsDisposed)overlay.Stop(null);if(other!=null&&!other.IsDisposed)other.Close();});}catch{}
                }
            }
        }
        static void VerifyCapturePolicies() {
            // Rich accessible toolbar text must never suppress a custom grid's OCR fallback.
            for(int sample=1;sample<=20;sample++) {
                if(Helper.NeedsOcr(100,sample)!=(sample%5==0))throw new Exception("periodic_ocr");
                if(!Helper.NeedsOcr(0,sample))throw new Exception("empty_uia_ocr");
            }
            if(!Helper.FullyVisibleBounds(0,0,100,80,100,80))throw new Exception("visible_bounds");
            if(Helper.FullyVisibleBounds(90,5,20,20,100,80))throw new Exception("clipped_right");
            if(Helper.FullyVisibleBounds(5,70,20,20,100,80))throw new Exception("clipped_bottom");
            if(Helper.FullyVisibleBounds(-1,5,20,20,100,80))throw new Exception("clipped_left");
            if(Helper.FullyVisibleBounds(5,-1,20,20,100,80))throw new Exception("clipped_top");
            if(Helper.FullyVisibleBounds(double.NaN,5,20,20,100,80))throw new Exception("nonfinite_bounds");
        }
        sealed class OcrCanvas:Control {
            internal int FirstRow;
            public OcrCanvas(){DoubleBuffered=true;BackColor=Color.White;AccessibleName="Synthetic custom painted stock table";}
            protected override void OnPaint(PaintEventArgs e) {
                base.OnPaint(e);var g=e.Graphics;
                using(var font=new Font("Segoe UI",12))using(var bold=new Font("Segoe UI",12,FontStyle.Bold)) {
                    int[] x={10,210,510,620,710,870};
                    string[] headers={"Штрихкод","Название","Количество","Ед.","Цена","Категория"};
                    for(int c=0;c<x.Length;c++)g.DrawString(headers[c],bold,Brushes.Black,x[c],15);
                    for(int r=0;r<10;r++) {
                        int row=FirstRow+r,y=55+r*37;
                        string[] cells={(4601234567893L+row).ToString(),row==0?"Молоко 2,5%":"Synthetic item "+row,"12,5",row%2==0?"kg":"g","600,00","Dairy"};
                        for(int c=0;c<x.Length;c++)g.DrawString(cells[c],font,Brushes.Black,x[c],y);
                    }
                }
            }
        }
    }
}
