"""Offline end-to-end test: fake adb + fake agent, no TV touched.  python -m unittest tests.test_offline"""
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
PY = sys.executable.replace("\\", "/")
os.environ["ADB"] = f'"{PY}" "{HERE.replace(chr(92), "/")}/fake_adb.py"'
sys.path.insert(0, REPO)

import tv  # noqa: E402  (reads $ADB at import)
from tvfreedom_core import adbio, agent, discover, guard, known, mdns, onboard, plan, restore, verify  # noqa: E402

HOST = "192.168.1.20:5555"


class Offline(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        os.environ["FAKE_ADB_DIR"] = self.tmp
        shutil.copytree(os.path.join(REPO, "lists"), os.path.join(self.tmp, "lists"))
        shutil.copytree(os.path.join(REPO, "data"), os.path.join(self.tmp, "data"))
        self.patches = [mock.patch.object(tv, "ROOT", self.tmp),
                        mock.patch.object(known, "LISTS", os.path.join(self.tmp, "lists")),
                        mock.patch.object(onboard, "CONFIG", os.path.join(self.tmp, "tvfreedom.json")),
                        mock.patch.object(onboard, "OLD_CONFIG", os.path.join(self.tmp, "tvgo.json")),
                        mock.patch("time.sleep")]
        for p in self.patches:
            p.start()
        self.fake_agent = f'"{PY}" "{HERE.replace(chr(92), "/")}/fake_agent.py"'

    def tearDown(self):
        for p in self.patches:
            p.stop()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def writes(self):
        path = os.path.join(self.tmp, "writes.log")
        return open(path).read().splitlines() if os.path.exists(path) else []

    def test_discover_and_identify(self):
        cands = [("192.168.1.20:5555", "test")]
        tvs = discover.identify_tvs(cands)
        self.assertEqual(tvs[0]["model"], "MiTV-MOOR0")
        self.assertIn("192.168.1.20:5555", [a for _k, a, _n in discover.mdns()][0])

    def test_guard(self):
        self.assertEqual(guard.keep_reason("com.google.android.apps.mediashell"), "cast")
        self.assertEqual(guard.keep_reason("com.google.android.katniss"), "voice")
        self.assertEqual(guard.keep_reason("com.xiaomi.mitv.updateservice"), "OTA")
        self.assertEqual(guard.keep_reason("com.foo.bar", uid=1000), "core (system uid)")
        self.assertEqual(guard.keep_reason("com.foo.home", home={"com.foo.home"}), "launcher/setup (HOME)")
        self.assertEqual(guard.keep_reason("com.miui.tv.analytics"), "")
        self.assertEqual(guard.keep_reason("tv.alphonso.alphonso_eula"), "")

    def test_agent_parse_is_strict(self):
        asked = {"a.b", "c.d"}
        text = 'blah [{"pkg":"a.b","verdict":"REMOVE","phase":9},{"pkg":"x.y","verdict":"remove"}] tail'
        self.assertEqual(agent.parse(text, asked), {"a.b": ("remove", 3, "")})
        self.assertEqual(agent.parse("no json here", asked), {})
        broken = '{"pkg":"c.d","verdict":"keep","phase":1,"reason":"r"} and {"pkg": oops}'
        self.assertEqual(agent.parse(broken, asked), {"c.d": ("keep", 1, "r")})

    def test_plan_apply_verify_restore(self):
        self.assertTrue(adbio.connect(HOST))
        info = adbio.identify(HOST)
        p = plan.build(info, plan.facts(HOST), self.fake_agent)
        removed = {r["pkg"] for r in p["remove"]}
        # the careless agent said remove-all: the guard must still keep the essentials
        for pkg in ("com.google.android.apps.mediashell", "com.google.android.katniss", "android",
                    "com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith",
                    "com.netflix.ninja", "com.android.vending", "com.xiaomi.mitv.updateservice",
                    "com.google.android.inputmethod.latin", "mitv.service"):
            self.assertNotIn(pkg, removed, pkg)
        self.assertIn("com.miui.tv.analytics", removed)  # from this model's known list
        self.assertEqual(len(p["unanswered"]), 1)        # skipped by the agent -> kept
        conflicts = {c["pkg"] for c in p["conflicts"]}
        self.assertIn("com.xiaomi.mitv.updateservice", conflicts)
        self.assertTrue(all(r["action"] == "disable" for r in p["remove"]))  # default: no uninstalls
        pdir = plan.save(p, "test")
        self.assertTrue(os.path.exists(os.path.join(pdir, "plan.json")))
        self.assertEqual(self.writes(), [])  # planning changed nothing

        bdir = tv.backup("tvgo-mitv-moor0-FAKE123")
        applied = os.path.join(pdir, "applied.txt")
        with open(applied, "w") as fh:
            fh.writelines(f"{r['action']} {r['pkg']}\n" for r in p["remove"] if r["phase"] == 1)
        tv.HOST = HOST
        tv.apply(applied)
        w = self.writes()
        self.assertTrue(w and all(l.startswith("pm disable-user --user 0 ") for l in w))
        ok, host = verify.reboot_and_check(HOST, "FAKE123", pdir)
        self.assertTrue(ok)
        self.assertEqual(host, HOST)
        self.assertNotIn(applied, known.learned_files(os.path.dirname(pdir)))  # not learned until verified
        known.mark_verified(pdir, ok)

        # restore everything back to the backup: re-enables exactly what was disabled
        enable, reinstall = restore.diff_to_backup(HOST, bdir)
        self.assertEqual(set(enable), {l.split()[-1] for l in w})
        self.assertEqual(reinstall, [])
        # a later run reuses what was applied as this model's known list
        self.assertIn(applied, known.learned_files(os.path.dirname(pdir)))
        self.assertIn("com.miui.tv.analytics", known.for_model("MiTV-MOOR0"))
        self.assertIn(applied, restore.phase_lists("MiTV-MOOR0"))

        # restoring the backup undoes the run, so it stops being learned
        restore.restore_all(HOST, bdir, "MiTV-MOOR0")
        self.assertEqual(adbio.packages(HOST, "-d"), set())
        self.assertNotIn(applied, known.learned_files(os.path.dirname(pdir)))

        # so does restoring the run's own list
        known.mark_verified(pdir)
        restore.restore_list(HOST, applied)
        self.assertNotIn(applied, known.learned_files(os.path.dirname(pdir)))

    def test_uninstall_and_restore_list(self):
        adbio.connect(HOST)
        tv.HOST = HOST
        path = os.path.join(self.tmp, "l.txt")
        open(path, "w").write("uninstall com.sonyliv\ndisable com.xm.webcontent\n")
        tv.apply(path)
        self.assertNotIn("com.sonyliv", adbio.packages(HOST))
        restore.restore_list(HOST, path)
        self.assertIn("com.sonyliv", adbio.packages(HOST))
        self.assertEqual(adbio.packages(HOST, "-d"), set())

    def test_pick_tv_points_tv_py_at_the_chosen_tv(self):
        import argparse
        import tvfreedom
        a, b = "192.168.1.20:5555", "192.168.1.21:5555"
        args = argparse.Namespace(host=None, subnet=None)
        with mock.patch.object(discover, "find", return_value=([(a, "x"), (b, "y")], [])), \
                mock.patch.object(tvfreedom, "ask", return_value="1"):
            info = tvfreedom.pick_tv(args)
        self.assertEqual(info["host"], a)
        self.assertEqual(tv.HOST, a)  # not b, the last device discovery connected to

    def test_agent_reason_cannot_inject_list_lines(self):
        text = r'[{"pkg":"a.b","verdict":"remove","phase":1,"reason":"ads\nuninstall com.android.systemui"}]'
        reason = agent.parse(text, {"a.b"})["a.b"][2]
        self.assertEqual(reason, "ads uninstall com.android.systemui")

    def test_scan_without_lan_address_does_not_crash(self):
        with mock.patch.object(discover, "local_ip", return_value=""):
            self.assertEqual(discover.scan(), [])

    def test_split_cmd_keeps_windows_backslashes(self):
        cmd = r'"C:\Program Files\adb\adb.exe" -P 5037'
        self.assertEqual(tv.split_cmd(cmd, windows=True), [r"C:\Program Files\adb\adb.exe", "-P", "5037"])
        self.assertEqual(tv.split_cmd("gemini -p ''", windows=True), ["gemini", "-p", ""])
        self.assertEqual(tv.split_cmd("gemini -p ''", windows=False), ["gemini", "-p", ""])

    def test_stale_transport_is_dropped_before_reconnect(self):
        calls = []
        states = iter(["offline", "device"])
        with mock.patch.object(adbio, "adb", side_effect=lambda *a, **k: calls.append(a) or ""), \
                mock.patch.object(adbio, "state", side_effect=lambda h: next(states)):
            self.assertTrue(adbio.connect(HOST, wake=False))
        self.assertIn(("disconnect", HOST), calls)

    def test_unfinished_setup_runs_again(self):
        import argparse
        import tvfreedom
        args = argparse.Namespace(host=HOST, subnet=None, agent=self.fake_agent, uninstall_user_apps=False)
        with mock.patch.object(onboard, "check_host", return_value=True),                 mock.patch.object(onboard, "check_agent", return_value=False):
            self.assertFalse(onboard.run(tvfreedom.ask, self.fake_agent, host=HOST))
        self.assertIs(onboard.load()["ready"], False)
        self.assertNotIn("agent", onboard.load())  # a failed agent is asked for again, not reused
        with mock.patch.object(onboard, "run", return_value=False) as setup:
            with self.assertRaises(SystemExit):
                tvfreedom.make_plan(args)  # setup offered again, nothing planned
        setup.assert_called_once()
        self.assertEqual(self.writes(), [])

    def test_mdns_parser_reads_ptr_srv_a(self):
        import socket
        import struct

        def name(n):
            return b"".join(bytes([len(x)]) + x.encode() for x in n.split(".")) + b"\0"

        def rr(n, rtype, rdata):
            return name(n) + struct.pack(">HHIH", rtype, 1, 120, len(rdata)) + rdata
        svc, inst, host = "_adb._tcp.local", "adb-TV1._adb._tcp.local", "tv1.local"
        pkt = (struct.pack(">HHHHHH", 0, 0x8400, 0, 3, 0, 0) + rr(svc, mdns.PTR, name(inst))
               + rr(inst, mdns.SRV, struct.pack(">HHH", 0, 0, 5555) + name(host))
               + rr(host, mdns.A, socket.inet_aton("192.168.1.20")))
        recs = mdns.parse(pkt)
        self.assertIn((mdns.SRV, inst, (5555, host)), recs)
        self.assertIn((mdns.A, host, "192.168.1.20"), recs)
        self.assertEqual(mdns.parse(b"junk"), [])

    def test_new_brand_has_no_known_list(self):
        self.assertEqual(known.for_model("BRAVIA 4K VH2"), {})
        self.assertTrue(known.model_dir("BRAVIA 4K VH2").endswith(os.path.join("lists", "bravia-4k-vh2")))
        self.assertEqual(guard.keep_reason("com.sony.dtv.tvx"), "vendor HAL")

    def test_home_screen_packages_are_left_to_the_app(self):
        """Like the app: the Mi Box's phase 4/5 lists (Google TV launcher, setup, screensaver) are never planned."""
        f = {"paths": {p: "/system/app/x.apk" for p in ("com.google.android.apps.tv.launcherx",
                                                         "com.google.android.apps.tv.dreamx")},
             "disabled": set(), "user": {"com.plexapp.android"}, "home": set(), "uids": {}}
        info = {"host": HOST, "brand": "Xiaomi", "model": "MIBOX4", "device": "oneday", "android": "9", "ime": ""}
        p = plan.build(info, f, self.fake_agent)
        self.assertEqual(p["remove"], [])
        self.assertEqual(p["kept"]["com.google.android.apps.tv.dreamx"], plan.HOME_OWNED)
        self.assertNotEqual(p["kept"]["com.google.android.apps.tv.launcherx"], "")  # the guard or HOME_OWNED
        self.assertEqual(p["user_apps"], ["com.plexapp.android"])
        with mock.patch("builtins.print") as out:
            plan.show(p)
        shown = "\n".join(str(c.args[0]) for c in out.call_args_list)
        self.assertIn("TVFreedom app", shown)
        self.assertIn("com.google.android.apps.tv.dreamx", shown)


if __name__ == "__main__":
    unittest.main()
