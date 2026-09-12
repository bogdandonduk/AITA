import contextlib
import importlib.util
import io
from pathlib import Path
import stat
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('company_admin',Path(__file__).with_name('company-admin.py'))
admin=importlib.util.module_from_spec(spec);spec.loader.exec_module(admin)
USER='e442dc11-ad53-4213-9844-e5ee7d830a22'
EMP='d38d7035-222d-4c5b-a2c3-461a614454f1'

class CompanyAdminTest(unittest.TestCase):
    def args(self,*values):
        return admin.parser().parse_args([*values,'--operator-user-id',USER,'--reason','Approved employment','--sql','unused.sql'])
    def test_no_connection_or_credentials_are_built_in(self):
        source=Path(admin.__file__).read_text()
        self.assertNotIn('import psycopg',source)
        self.assertNotIn('subprocess',source)
        self.assertNotIn('DB_PASSWORD',source)
    def test_employ_preserves_attribution_and_uses_known_job(self):
        sql=admin.build_sql(self.args('employ','--user-id',USER,'--job','support_agent'))
        self.assertIn('aita.operator_user_id',sql);self.assertIn('Approved employment',sql)
        self.assertIn('support_agent',sql);self.assertIn('pg_advisory_xact_lock',sql)
        self.assertIn('INSERT INTO company_employments',sql)
        self.assertNotIn('CREATE USER',sql)
    def test_multiple_jobs_do_not_replace_old_assignments(self):
        sql=admin.build_sql(self.args('assign','--employment-id',EMP,'--job','support_agent','--job','support_supervisor'))
        self.assertIn('support_agent',sql);self.assertIn('support_supervisor',sql)
        self.assertNotIn('DELETE FROM company_job_assignments',sql)
    def test_duplicate_and_malicious_job_ids_are_rejected(self):
        for jobs in (['support_agent','support_agent'],["support_agent';DROP TABLE users;--"]):
            args=['employ','--user-id',USER]
            for job in jobs:args.extend(['--job',job])
            with self.assertRaises(ValueError):admin.build_sql(self.args(*args))
    def test_uuid_requires_canonical_form(self):
        self.assertEqual(USER,admin.user_id(USER.upper()))
        for bad in ('1-1-1-1-1',USER.replace('-',''),'not-a-uuid'):
            with self.assertRaises(ValueError):admin.user_id(bad)
    def test_temporal_boundaries(self):
        self.assertEqual(0,admin.timestamp('1970-01-01T00:00:00Z'))
        self.assertEqual(admin.timestamp('2026-09-12T00:00:00Z'),admin.timestamp('2026-09-12T05:00:00+05:00'))
        with self.assertRaises(ValueError):admin.timestamp('2026-09-12T00:00:00')
        with self.assertRaises(ValueError):admin.build_sql(self.args('employ','--user-id',USER,'--job','support_agent',
            '--starts-at','2026-09-12T00:00:00Z','--ends-at','2026-09-12T00:00:00Z'))
    def test_generated_scripts_never_replace_existing_files(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'hire.sql'
            args=['employ','--user-id',USER,'--job','support_agent','--operator-user-id',USER,'--reason','Approved','--sql',str(path)]
            with contextlib.redirect_stdout(io.StringIO()):self.assertEqual(0,admin.main(args))
            original=path.read_bytes();self.assertEqual(0o600,stat.S_IMODE(path.stat().st_mode))
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):admin.main(args)
            self.assertEqual(original,path.read_bytes())
    def test_reason_and_titles_do_not_enter_the_dollar_quoted_program(self):
        args=self.args('job','--job-id','custom_support','--title-en',"O'Brien $aita_company$",'--title-ru','Название','--title-kk','Атау','--capability','support.queue.read')
        args.reason="Approved $aita_company$ ' by operator"
        sql=admin.build_sql(args)
        program=sql.split('DO $aita_company$',1)[1]
        self.assertNotIn("O'Brien",program);self.assertNotIn('by operator',program)
        self.assertIn("O''Brien",sql)
    def test_status_change_releases_work_only_if_access_is_gone(self):
        sql=admin.build_sql(self.args('status','--employment-id',EMP,'--status','suspended'))
        self.assertIn('company_support_agent_access',sql);self.assertIn('employment_access_removed',sql)
        self.assertIn('Ended employment cannot resume',sql)
    def test_unassign_preserves_history(self):
        sql=admin.build_sql(self.args('unassign','--assignment-id',EMP))
        self.assertIn('SET is_active=FALSE',sql);self.assertNotIn('DELETE FROM company_employments',sql)
    def test_read_only_inspection_is_explicitly_scoped(self):
        args=admin.parser().parse_args(['inspect','--user-id',USER,'--sql','inspect.sql'])
        sql=admin.build_sql(args)
        self.assertIn('BEGIN READ ONLY',sql);self.assertIn(USER,sql);self.assertNotIn('INSERT',sql)
    def test_invalid_inputs_write_nothing(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'hire.sql'
            with contextlib.redirect_stderr(io.StringIO()),self.assertRaises(SystemExit):admin.main([
                'employ','--user-id','invalid','--job','support_agent','--operator-user-id',USER,'--reason','Approved','--sql',str(path)])
            self.assertFalse(path.exists())

if __name__=='__main__':unittest.main()
