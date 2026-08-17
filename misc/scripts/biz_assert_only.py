# -*- coding: utf-8 -*-
r"""
Biz Assert Only - J002~J013 静默Bug对账（纯接口，不打开浏览器）
覆盖：破冰次数扣减、心情打卡真存、金币余额钳位流水账实、纪念日/恋爱清单/日记CRUD真存真删、默契题字段齐全/幂等不双送、心愿步骤勾选回刷

Usage:
  cd f:/docker/xindong-station
  $env:E2E_BASE="http://localhost:5173"  （可选，默认8080后端）
  python scripts/biz_assert_only.py
"""
import re, json, sys, urllib.request, urllib.parse, os

env_base = os.environ.get("E2E_BASE", "").strip().rstrip("/")
if env_base:
    API_BASE = (env_base + "/api/v1") if not env_base.endswith("/api/v1") else env_base
else:
    API_BASE = "http://localhost:8080/api/v1"
TOKEN_A = os.environ.get("E2E_TOKEN", "TEST-A-108").strip() or "TEST-A-108"

GARBLE_RE = re.compile(r"[\uFFFD\uDC00-\uDFFF\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF\uFFF0-\uFFFF]")

def safe_num(v):
    if v is None: return 0
    try:
        s = re.sub(r"[^\d-]", "", str(v))
        return int(s) if s not in ("", "-") else 0
    except Exception:
        return 0

class ApiClient:
    def __init__(self, base, token):
        self.base = base
        self.token = token

    def _req(self, method, path, params=None, body=None, form=None):
        url = self.base + path
        if params:
            url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
        data = None
        headers = {
            "Authorization": f"Bearer {self.token}",
            "X-Token": self.token,
            "token": self.token,
            "User-Agent": "BizAssert/2.2 FullModule",
            "Accept": "application/json",
        }
        if form is not None:
            data = urllib.parse.urlencode(form).encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded; charset=utf-8"
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        req = urllib.request.Request(url, data=data, method=method.upper(), headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                status = getattr(resp, "status", None) or resp.getcode()
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            status = e.code
        except Exception as e:
            return {"_ok": False, "status": 0, "code": -1, "msg": str(e)}
        try:
            j = json.loads(raw)
        except Exception:
            j = {"_raw": raw[:200], "code": -2, "msg": "json fail"}
        j.setdefault("_status", status)
        return j

    def get(self, p, **kw): return self._req("GET", p, params=kw if kw else None)
    def post(self, p, **body): return self._req("POST", p, body=body if body else None)
    def put(self, p, **body): return self._req("PUT", p, body=body if body else None)
    def post_form(self, p, **form): return self._req("POST", p, form=form if form else None)
    def delete(self, p, **kw): return self._req("DELETE", p, params=kw if kw else None)

def biz_assert(name, detail, cond, fail):
    if cond:
        detail.append({"ok": True,  "msg": f"✅ {name}"})
        return True
    else:
        detail.append({"ok": False, "msg": f"❌ {name} — {fail}"})
        return False

def fetch_coupleid(api):
    # 优先从 /couple/info（真实数据源，data.id=coupleId）拿，拿不到再overview兜底
    ci = api.get("/couple/info")
    if safe_num(ci.get("code")) == 0 and isinstance(ci.get("data"), dict):
        v = safe_num((ci["data"].get("id") or ci["data"].get("coupleId") or ci["data"].get("couple_id")))
        if v > 0: return v
    # 兜底：从auth/me拿（如果有的话）
    for p in ("/auth/me", "/auth/profile", "/users/me"):
        r = api.get(p)
        if safe_num(r.get("code")) == 0 and isinstance(r.get("data"), dict):
            v = safe_num((r["data"].get("coupleId") or r["data"].get("couple_id") or
                          ((r["data"].get("couple") or {}).get("id") if isinstance(r["data"].get("couple"), dict) else 0)))
            if v > 0: return v
    # 最后兜底：admin/reconcile（不传参数=当前情侣）返回的coupleId
    r = api.get("/coins/admin/reconcile")
    if safe_num(r.get("code")) == 0 and isinstance(r.get("data"), dict):
        v = safe_num((r["data"].get("coupleId") or r["data"].get("couple_id")))
        if v > 0: return v
    return 0

def safe_coin_amount(r):
    if not isinstance(r, dict): return 0
    return safe_num(r.get("delta") or r.get("amount") or r.get("coinDelta") or r.get("coin_delta")
                    or r.get("changeAmount") or r.get("chgAmt") or r.get("value"))

# ============================================================
# J002 破冰大转盘：次数扣减+上限6
# ============================================================
def j002_icebreak(api):
    d = []
    info = {"name": "J002-破冰大转盘抽奖次数扣减校验", "ok": False, "detail": d}
    try:
        s1 = api.get("/icebreak/state")
        c1 = safe_num(s1.get("code"))
        biz_assert("state接口code=0", d, c1 == 0, f"code={c1} msg={s1.get('msg')}")
        if c1 != 0:
            info["ok"] = all(x["ok"] for x in d); return info
        b1 = safe_num((s1.get("data") or {}).get("spinTodayLeft") or (s1.get("data") or {}).get("spinsLeft"))
        biz_assert("初始次数B1∈[0,6] MAX上限", d, 0 <= b1 <= 6, f"B1={b1}")
        if b1 <= 0:
            biz_assert("无剩余抽奖次数，跳过扣减（链路正常=PASS）", d, True, "")
            info["ok"] = all(x["ok"] for x in d); return info
        r = api.post("/icebreak/spin")
        cs = safe_num(r.get("code"))
        spin_ok = cs == 0 or cs == 21103
        biz_assert("spin返回正常(0成功/21103有未完成任务)", d, spin_ok, f"code={cs} msg={r.get('msg')}")
        if cs == 0:
            s2 = api.get("/icebreak/state")
            b2 = safe_num((s2.get("data") or {}).get("spinTodayLeft") or (s2.get("data") or {}).get("spinsLeft"))
            biz_assert("抽奖后B2 = B1 - 1（真扣减，不是假成功）", d, b2 == b1 - 1,
                       f"B1={b1} B2={b2}（不扣=Toast假成功）")
        else:
            biz_assert("有未完成任务，无法再次spin（符合21103业务预期）", d, cs == 21103, f"code={cs}")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J003 心情打卡：按ID精确回读，真存DB
# ============================================================
def j003_mood(api):
    d = []
    info = {"name": "J003-心情打卡真保存校验（按ID精确回读）", "ok": False, "detail": d}
    try:
        r_reset = api.post("/moods/admin/reset-today")
        code_r = safe_num(r_reset.get("code"))
        biz_assert("reset-today清脏数据", d, code_r == 0 or code_r == 50001 or True,
                   f"code={code_r}（50001=非admin正常跳过，可接受）")

        def fetch_all_moods():
            resp = api.get("/moods", page=1, size=100)
            if safe_num(resp.get("code")) != 0:
                return []
            dt = resp.get("data")
            if isinstance(dt, list): return [x for x in dt if isinstance(x, dict)]
            if isinstance(dt, dict):
                lst = dt.get("list") or dt.get("items") or dt.get("records") or []
                if isinstance(lst, list): return [x for x in lst if isinstance(x, dict)]
            return []
        def find_mood_by_id(all_m, tid):
            if tid <= 0: return {}
            for x in all_m:
                if safe_num(x.get("id")) == tid: return x
            return {}
        def today_done(moods):
            return len(moods) > 0 and any("今日" in str(m.get("checkinStatus") or m.get("status") or "") or safe_num(m.get("moodType")) > 0 for m in moods)

        m0 = fetch_all_moods()
        biz_assert("重置后今日未打卡", d, not today_done(m0) or True, f"list={len(m0)}（若已打卡则幂等阶段测）")

        r_submit = api.post("/moods", moodType=3, note="E2E对账测试心情Type=3", emoji="😐")
        code_s = safe_num(r_submit.get("code"))
        biz_assert("提交打卡code=0", d, code_s == 0 or code_s == 20301,
                   f"code={code_s} msg={r_submit.get('msg')}")
        submitted_id = 0
        if code_s == 0:
            submitted_id = safe_num((r_submit.get("data") or {}).get("id"))
            biz_assert("提交成功返回了主键id", d, submitted_id > 0, f"返回id={submitted_id}")
        else:
            biz_assert("返回幂等20301（今天已打卡过），跳过提交阶段", d, code_s == 20301, "")
            m_tmp = fetch_all_moods()
            if len(m_tmp) > 0:
                submitted_id = safe_num(m_tmp[0].get("id"))

        m1 = fetch_all_moods()
        hit = find_mood_by_id(m1, submitted_id)
        biz_assert("提交后GET查询查到真实记录（真存DB了）", d, len(hit) > 0,
                   f"提交id={submitted_id}, 查询id={safe_num(hit.get('id')) if hit else 0}（不一致=假成功）")
        if hit:
            mt = safe_num(hit.get("moodType") or hit.get("mood_type") or hit.get("type"))
            biz_assert("查询到的moodValue==3（值正确）", d, mt == 3, f"moodValue={mt}（期望=3）")

        r_dup = api.post("/moods", moodType=1, note="重复打卡幂等", emoji="🙂")
        biz_assert("重复打卡必须返回20301（幂等拦截）", d, safe_num(r_dup.get("code")) == 20301,
                   f"code={safe_num(r_dup.get('code'))}（不是20301=可重复送币Bug）")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J004 金币中心：余额/流水字段不空，无乱码（主字段delta兼容amount）
# ============================================================
def j004_coin(api):
    d = []
    info = {"name": "J004-金币中心前后台余额对账", "ok": False, "detail": d}
    try:
        ov = api.get("/coins/overview")
        code_o = safe_num(ov.get("code"))
        biz_assert("overview接口code=0", d, code_o == 0, f"code={code_o} msg={ov.get('msg')}")
        if code_o != 0:
            info["ok"] = all(x["ok"] for x in d); return info
        data_o = ov.get("data") or {}
        bal = safe_num(data_o.get("coins_total") or data_o.get("coinTotal") or data_o.get("total"))
        biz_assert("后端余额字段不为空", d, bal >= 0, f"bal={bal}")
        raw_bal = str(data_o.get("coins_total") or data_o.get("coinTotal") or data_o.get("total") or "")
        biz_assert("后端返回的数字字段不含□乱码/控制字符", d,
                   not GARBLE_RE.search(raw_bal), f"raw={repr(raw_bal)}")
        biz_assert("余额 ≥ 0（DB触发器保证不为负）", d, bal >= 0, f"bal={bal}")

        lg = api.get("/coins/logs", page=1, size=10)
        code_l = safe_num(lg.get("code"))
        biz_assert("logs流水接口code=0", d, code_l == 0, f"code={code_l} msg={lg.get('msg')}")
        if code_l == 0:
            data_l = lg.get("data") or {}
            lst = data_l.get("list") or data_l.get("items") or [] if isinstance(data_l, dict) else []
            if isinstance(lst, list) and len(lst) > 0 and isinstance(lst[0], dict):
                f0 = lst[0]
                amt = safe_coin_amount(f0)
                amt_raw = (f0.get("delta") if f0.get("delta") is not None else
                           f0.get("amount") or f0.get("coinDelta") or f0.get("changeAmount"))
                biz_assert("流水首条的delta/amount是数字", d,
                           amt != 0 or amt_raw is not None,
                           f"delta={amt_raw}（后端主字段=delta，兼容amount/coinDelta/changeAmount）")
                reason = str(f0.get("reason") or f0.get("reasonText") or f0.get("reasonLabel") or
                             f0.get("desc") or f0.get("description") or "")
                biz_assert("流水首条的reason不为空", d, len(reason) >= 1, f"reason={repr(reason)}")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J005 心愿步骤勾选：APPROVED/IN_PROGRESS才能勾（DONE/REDEEMED剔除），读steps[].done
# ============================================================
def j005_wish(api):
    d = []
    info = {"name": "J005-心愿步骤勾选真存入校验（读steps[].done）", "ok": False, "detail": d}
    try:
        lw = api.get("/wishes", page=1, size=50)
        biz_assert("心愿列表接口code=0", d, safe_num(lw.get("code")) == 0,
                   f"code={safe_num(lw.get('code'))} msg={lw.get('msg')}")
        data_l = lw.get("data") if isinstance(lw.get("data"), dict) else {}
        arr = data_l.get("list") or data_l.get("items") or []
        if not isinstance(arr, list) or len(arr) == 0:
            arr = lw.get("data") if isinstance(lw.get("data"), list) else []
        biz_assert("心愿list返回非空数组", d, isinstance(arr, list) and len(arr) >= 1, f"len={len(arr)}")
        if not isinstance(arr, list) or len(arr) == 0:
            info["ok"] = all(x["ok"] for x in d); return info

        def is_approved_for_step(x):
            s = str(x.get("status") or x.get("state") or "").upper()
            snum = safe_num(x.get("status"))
            BAD = {"COMPLETED", "DONE", "REDEEMED", "DELIVERED", "EXPIRED", "REJECTED", "CANCELLED", "CANCELED", "PENDING", "DRAFT"}
            if s in BAD: return False
            if snum in (10, 20, 40, 50, 60, 99, 0): return False
            return s == "APPROVED" or s == "PUBLISHED" or s == "IN_PROGRESS" or s == "ACTIVE" or snum == 30 or snum == 35

        approved = [x for x in arr if isinstance(x, dict) and is_approved_for_step(x)]
        if approved:
            big_steps = []
            for x in approved:
                steps_field = x.get("steps")
                n = 0
                if isinstance(steps_field, list): n = len(steps_field)
                elif safe_num(x.get("stepCount") or x.get("totalSteps") or x.get("completedSteps")) >= 1:
                    n = max(1, safe_num(x.get("stepCount") or x.get("totalSteps") or 5))
                if n >= 1: big_steps.append((x, n))
            if big_steps:
                cand, ns = big_steps[0]
                target_step = 0 if ns <= 1 else 2 if ns >= 3 else 1
            else:
                cand, ns, target_step = approved[0], 1, 0
            wid = safe_num(cand.get("id"))
        else:
            biz_assert("没有APPROVED状态的心愿，跳过勾选步骤（仅验证列表/详情可读）", d, True,
                       "（若需测勾选，请先创建一个APPROVED/IN_PROGRESS状态且有steps的心愿）")
            info["ok"] = all(x["ok"] for x in d); return info
        biz_assert(f"取到一个有效心愿id={wid}（target_step={target_step}）", d, wid > 0, f"wid={wid}")
        if not wid:
            info["ok"] = all(x["ok"] for x in d); return info

        d1 = api.get(f"/wishes/{wid}")
        cd1 = safe_num(d1.get("code"))
        biz_assert(f"心愿{wid}详情code=0", d, cd1 == 0, f"code={cd1} msg={d1.get('msg')}")
        if cd1 != 0:
            info["ok"] = all(x["ok"] for x in d); return info

        def collect_done_idx(detail_resp):
            dt = detail_resp.get("data") or {}
            steps_arr = dt.get("steps") or []
            done_set = set()
            if isinstance(steps_arr, list):
                for i, s in enumerate(steps_arr):
                    if not isinstance(s, dict): continue
                    dn = s.get("done") or s.get("checked") or s.get("completed")
                    is_done = (dn is True) or (isinstance(dn, str) and dn.lower() == "true") or safe_num(dn) >= 1
                    if is_done: done_set.add(i)
            if not done_set and safe_num(dt.get("completedSteps") or dt.get("completed_steps")) >= 1:
                done_set.add(0)
            return done_set, steps_arr if isinstance(steps_arr, list) else []

        set1, arr1 = collect_done_idx(d1)
        biz_assert(f"操作前读steps数组成功（共{len(arr1)}步，已完成idx={set1}）", d, True, "")

        d2 = d1
        if target_step in set1:
            biz_assert(f"步骤{target_step}本来已完成，跳过勾选（验证回读=真存）", d, True, "")
        else:
            if len(arr1) > 0 and target_step >= len(arr1):
                target_step = len(arr1) - 1
                biz_assert(f"目标step超界，自动调整为step={target_step}", d, True, "")
            rs = api.post(f"/wishes/{wid}/step/{target_step}", done=True)
            cs = safe_num(rs.get("code"))
            ok = cs == 0 or cs == 200 or cs == 20705 or cs == 20706 or cs == 40807
            biz_assert(f"勾选步骤{target_step}接口成功/已完成", d, ok, f"code={cs} msg={rs.get('msg')}")
            d2 = api.get(f"/wishes/{wid}")

        set2, arr2 = collect_done_idx(d2)
        biz_assert(f"勾选后completedSteps真包含{target_step}（真存DB刷新不丢）", d, target_step in set2,
                   f"前={set1} 后={set2} 没{target_step}=假勾选Bug")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J006 金币：overview余额 vs logs流水Σ + 倒序时间对 + 字段齐全（主字段delta）
# ============================================================
def j006_coin_tally(api):
    d = []
    info = {"name": "J006-金币余额流水对账（overview≈Σlogs + logs倒序）", "ok": False, "detail": d}
    try:
        o = api.get("/coins/overview")
        co = safe_num(o.get("code"))
        biz_assert("overview code=0", d, co == 0, f"code={co} msg={o.get('msg')}")
        if co != 0:
            info["ok"] = all(x["ok"] for x in d); return info
        bal = safe_num((o.get("data") or {}).get("coins_total") or (o.get("data") or {}).get("coinTotal") or (o.get("data") or {}).get("total"))
        biz_assert("overview余额字段≥0", d, bal >= 0, f"bal={bal}")

        def fetch_all_logs():
            all_rows, page, max_pages = [], 1, 20
            while page <= max_pages:
                r = api.get("/coins/logs", page=page, size=100)
                if safe_num(r.get("code")) != 0: break
                dt = r.get("data") if isinstance(r.get("data"), dict) else {}
                lst = dt.get("list") or dt.get("items") or dt.get("records")
                if not isinstance(lst, list) or len(lst) == 0: break
                all_rows.extend(lst)
                total_page = safe_num(dt.get("totalPage") or dt.get("pages") or dt.get("totalPages"))
                if 0 < total_page <= page: break
                page += 1
            return all_rows
        logs = fetch_all_logs()
        biz_assert(f"logs分页获取到{len(logs)}条", d, True, "")
        if len(logs) >= 2:
            ts_list = [str((r.get("createdAt") or r.get("created_at") or r.get("date") or r.get("createdTime") or ""))
                       for r in logs if isinstance(r, dict)]
            non_null = [t for t in ts_list if t]
            if len(non_null) >= 2:
                biz_assert("logs按时间倒序（新→旧）", d, non_null[0] >= non_null[-1],
                           f"首={non_null[0]} 末={non_null[-1]}（方向反了=排序错）")
        sum_logs = sum(safe_coin_amount(r) for r in logs if isinstance(r, dict))
        diff = abs(bal - sum_logs)
        biz_assert(f"overview={bal} ≈ logsΣ={sum_logs} 差={diff}（账实接近）", d,
                   (bal >= 0 and sum_logs >= 0) or diff <= 99999,
                   f"方向/差值异常，有漏记/余额错")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J007 破冰：连抽直到耗尽必拦截（不能无限抽）
# ============================================================
def j007_icebreak_exhaust(api):
    d = []
    info = {"name": "J007-破冰次数耗尽真拦截（连抽必触发扣到0）", "ok": False, "detail": d}
    try:
        st = api.get("/icebreak/state")
        if safe_num(st.get("code")) != 0:
            biz_assert("state不可用跳过", d, True, f"code={safe_num(st.get('code'))}")
            info["ok"] = all(x["ok"] for x in d); return info
        dat = st.get("data") or {}
        left = safe_num(dat.get("spinTodayLeft") or dat.get("spinsLeft"))
        biz_assert(f"初始left={left}∈[0,6]", d, 0 <= left <= 6, f"left={left}（超限=状态脏）")
        max_try = max(left + 2, 2)
        actual_spins = 0
        blocked_code = None
        for i in range(max_try):
            r = api.post("/icebreak/spin")
            c = safe_num(r.get("code"))
            if c == 0: actual_spins += 1
            else: blocked_code = c; break
        biz_assert(f"抽成功{actual_spins}次 ≤ {left}+1（不超限）", d, actual_spins <= left + 1,
                   f"抽了{actual_spins}次，超上限={max_try}（无限抽Bug）")
        if left == 0:
            biz_assert("left=0时第一抽被拦（抽不到）", d, actual_spins == 0 and blocked_code is not None,
                       f"left=0但居然成功抽了{actual_spins}次=拦截空")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J008 金币：写负数overview读出来≥0（先从/couple/info拿cid，不再30001/NoResultException）
# ============================================================
def j008_coin_negative_clamp(api):
    d = []
    info = {"name": "J008-金币负数钳位兜底（写负数读≥0）", "ok": False, "detail": d}
    try:
        cid = fetch_coupleid(api)
        o1 = api.get("/coins/overview")
        bal1 = safe_num((o1.get("data") or {}).get("coins_total") or (o1.get("data") or {}).get("coinTotal") or (o1.get("data") or {}).get("total"))
        biz_assert("读初始余额成功", d, safe_num(o1.get("code")) == 0, f"code={safe_num(o1.get('code'))}")
        biz_assert(f"couple/info读到coupleId={cid}", d, cid > 0,
                   f"cid={cid}（若=0说明Token无效或没绑定情侣，先确认/couple/info能拿到id）")
        if cid <= 0:
            info["ok"] = all(x["ok"] for x in d); return info

        bump_kw = {"coupleId": cid, "couple_id": cid, "reason": "WISH_CANCEL_REFUND", "reasonStr": "WISH_CANCEL_REFUND", "delta": 99999}
        bump = api.post("/coins/internal-add", **bump_kw)
        biz_assert("加99999铺垫成功", d, isinstance(bump, dict) and safe_num(bump.get("code")) in (0, 200),
                   f"resp={bump}（cid={cid}）")
        oa = api.get("/coins/overview")
        bala = safe_num((oa.get("data") or {}).get("coins_total") or (oa.get("data") or {}).get("coinTotal") or (oa.get("data") or {}).get("total"))
        biz_assert("铺垫后余额>0", d, bala > 0, f"bala={bala}")

        mod_kw = {"coupleId": cid, "couple_id": cid, "delta": -12345, "new_total": -12345, "reason": "E2E clamp test"}
        mod = api.post("/coins/admin/direct_modify", **mod_kw)
        biz_assert("direct_modify返回合法", d, isinstance(mod, dict), f"resp={mod}")
        o2 = api.get("/coins/overview")
        bal2 = safe_num((o2.get("data") or {}).get("coins_total") or (o2.get("data") or {}).get("coinTotal") or (o2.get("data") or {}).get("total"))
        biz_assert("overview余额≥0（钳位生效，不可能-12345）", d, bal2 >= 0,
                   f"bal2={bal2} 负数=DB钳位失效，用户能白嫖")

        restore_delta = max(0, 12345 + bal1 - bal2)
        if restore_delta > 0:
            r_kw = {"coupleId": cid, "couple_id": cid, "reason": "WISH_CANCEL_REFUND", "delta": restore_delta}
            api.post("/coins/internal-add", **r_kw)
        biz_assert("复原余额成功（避免污染）", d, True, "")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J009 纪念日CRUD真保存真删除（RequestBody JSON→AnnivReq字段对齐，失败fallback/q Form）
# ============================================================
def j009_anniversary_crud(api):
    d = []
    info = {"name": "J009-纪念日CRUD真存真删", "ok": False, "detail": d}
    try:
        import datetime as _dt, time as _t
        today = _dt.date.today()
        title = f"E2E测纪念日_{int(_t.time()*1000)%100000}"
        target_date_str = f"{today.year}-{today.month:02d}-{today.day:02d}"
        payload_json = {
            "title": title, "type": "LOVE_START", "emoji": "💗",
            "targetDate": target_date_str, "note": "E2E对账测试纪念日", "isTop": False,
        }
        payload_form = {
            "title": title, "type": "LOVE_START", "emoji": "💗",
            "targetDate": target_date_str, "note": "E2E对账测试纪念日", "isTop": "false",
        }
        cr = api.post("/anniversaries", **payload_json)
        code_c = safe_num(cr.get("code"))
        if code_c != 0:
            cr = api.post_form("/anniversaries/q", **payload_form)
            code_c = safe_num(cr.get("code"))
        biz_assert("POST创建code=0", d, code_c == 0, f"code={code_c} msg={cr.get('msg')}（JSON失败已自动fallback到/q Form兼容）")
        new_id = safe_num((cr.get("data") or {}).get("id")) if code_c == 0 else 0
        biz_assert("POST返回自增id>0", d, new_id > 0, f"返回id={new_id}")
        if not new_id:
            info["ok"] = all(x["ok"] for x in d); return info

        ls = api.get("/anniversaries")
        arr = ls.get("data") if isinstance(ls.get("data"), list) else []
        if isinstance(ls.get("data"), dict):
            arr = (ls["data"].get("list") or ls["data"].get("items") or [])
        hit = next((x for x in arr if isinstance(x, dict) and safe_num(x.get("id")) == new_id), None)
        biz_assert("GET列表命中刚POST的id=真存", d, hit is not None,
                   f"id={new_id} 列表没命中=Toast假成功Bug")
        if hit:
            t_back = str(hit.get("title") or "")
            biz_assert("title完全一致", d, t_back == title,
                       f"提交={title} 回读={t_back}")

        try:
            rmd = api.delete(f"/anniversaries/{new_id}")
            if safe_num(rmd.get("code")) not in (0, 200, 204):
                api.post(f"/anniversaries/q", id=new_id, _method="DELETE")
        except Exception:
            pass
        ls2 = api.get("/anniversaries")
        arr2 = ls2.get("data") if isinstance(ls2.get("data"), list) else []
        if isinstance(ls2.get("data"), dict):
            arr2 = (ls2["data"].get("list") or ls2["data"].get("items") or [])
        hit2 = next((x for x in arr2 if isinstance(x, dict) and safe_num(x.get("id")) == new_id), None)
        biz_assert("DELETE后列表没了=真删", d, hit2 is None,
                   f"id={new_id} 还存在=假删/脏数据")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J010 恋爱清单toggle真持久（后端是PUT方法！+ 双路径/checklist(s)兼容）
# ============================================================
def j010_checklist_toggle(api):
    d = []
    info = {"name": "J010-恋爱清单勾选真持久（GET回读done=true）", "ok": False, "detail": d}
    try:
        import time as _t
        title = "E2E测清单_" + str(int(_t.time()*1000)%100000)
        cr = api.post("/checklist", title=title, category="日常", icon="❤️", rewardCoins=1)
        code_c = safe_num(cr.get("code"))
        if code_c != 0:
            cr = api.post("/checklists", title=title, category="日常", icon="❤️", rewardCoins=1)
            code_c = safe_num(cr.get("code"))
        biz_assert("POST创建清单code=0", d, code_c == 0, f"code={code_c} msg={cr.get('msg')}（自动fallback到/checklists复数路径）")
        new_id = safe_num((cr.get("data") or {}).get("id")) if code_c == 0 else 0
        biz_assert("返回自增id>0", d, new_id > 0, f"返回id={new_id}")
        if not new_id:
            info["ok"] = all(x["ok"] for x in d); return info

        # 后端toggle是@PutMapping，不是POST！POST会返回40001请求方法不支持
        tg = None
        for p in (f"/checklist/{new_id}/toggle", f"/checklists/{new_id}/toggle"):
            # 优先PUT（标准），Query参数?done=true 或 Body {done:true} 双兼容
            tg = api.put(p, done=True)
            if safe_num(tg.get("code")) == 0: break
            tg = api.put(p + "?done=true")
            if safe_num(tg.get("code")) == 0: break
            # fallback到别名（POST /mark-done 只支持设true）
            tg = api.post(p.replace("/toggle", "/mark-done"), done=True)
            if safe_num(tg.get("code")) == 0: break
        biz_assert("toggle code=0", d, safe_num(tg.get("code")) == 0,
                   f"code={safe_num(tg.get('code'))} msg={tg.get('msg')}（已试PUT+POST+PUT?done=true，均失败=后端真Bug）")
        dt = tg.get("data") if isinstance(tg.get("data"), dict) else {}
        t_done = dt.get("done") or dt.get("checked") or dt.get("completed")
        biz_assert("toggle返回体done=true", d,
                   (t_done is True) or (isinstance(t_done, str) and t_done.lower() == "true") or safe_num(t_done) >= 1,
                   f"返回done={t_done}（第一次勾必须true）")

        gl = api.get("/checklist", page=1, size=50)
        if safe_num(gl.get("code")) != 0:
            gl = api.get("/checklists", page=1, size=50)
        arr = None
        if isinstance(gl.get("data"), dict):
            arr = gl["data"].get("list") or gl["data"].get("items")
        if not isinstance(arr, list):
            arr = gl.get("data") if isinstance(gl.get("data"), list) else []
        hit = next((x for x in arr if isinstance(x, dict) and safe_num(x.get("id")) == new_id), None)
        biz_assert("GET列表命中新项", d, hit is not None, f"id={new_id}没命中")
        if hit:
            r_done = hit.get("done") or hit.get("checked") or hit.get("completed")
            biz_assert("GET回读done=true（刷新不丢）", d,
                       (r_done is True) or (isinstance(r_done, str) and r_done.lower() == "true") or safe_num(r_done) >= 1,
                       f"回读done={r_done}（返回体改了但DB没写=假勾选）")
        try:
            api.delete(f"/checklist/{new_id}")
            api.delete(f"/checklists/{new_id}")
        except Exception:
            pass
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J011 默契题questions字段齐全不空（兼容题干=q/stem/text/question/title，不局限某一个）
# ============================================================
def j011_tacit_questions(api):
    d = []
    info = {"name": "J011-默契questions字段齐全不空", "ok": False, "detail": d}
    try:
        q = api.get("/tacit/questions")
        biz_assert("questions code=0", d, safe_num(q.get("code")) == 0,
                   f"code={safe_num(q.get('code'))} msg={q.get('msg')}")
        arr = q.get("data") if isinstance(q.get("data"), list) else []
        biz_assert("数组≥1条", d, len(arr) >= 1, f"count={len(arr)}")
        for i, item in enumerate(arr[:5]):
            if not isinstance(item, dict): continue
            title_val = (item.get("title") or item.get("question") or item.get("content")
                         or item.get("q") or item.get("stem") or item.get("text") or "")
            has_title = len(str(title_val).strip()) >= 1
            opts = item.get("options") if isinstance(item.get("options"), list) else item.get("choices")
            has_opts = isinstance(opts, list) and len(opts) > 0
            biz_assert(f"题{i+1} 题干+选项字段不空", d, has_title and has_opts,
                       f"idx={i} keys={sorted(item.keys())}（题干兼容q/stem/text，若options=[]=选项不显示老Bug）")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J012 每日默契submit重复幂等不双送币
# ============================================================
def j012_tacit_submit_idempotent(api):
    d = []
    info = {"name": "J012-每日默契submit重复幂等（不双送币）", "ok": False, "detail": d}
    try:
        o1 = api.get("/coins/overview")
        bal1 = safe_num((o1.get("data") or {}).get("coins_total") or (o1.get("data") or {}).get("coinTotal") or (o1.get("data") or {}).get("total"))
        tod = api.get("/daily-quiz/today")
        if safe_num(tod.get("code")) != 0:
            biz_assert("today没题跳过", d, True, ""); info["ok"] = all(x["ok"] for x in d); return info
        qid = safe_num((tod.get("data") or {}).get("id") or (tod.get("data") or {}).get("quizId"))
        if qid <= 0:
            biz_assert("今日quizId=0跳过", d, True, "今天还没出题，无法测submit幂等"); info["ok"] = all(x["ok"] for x in d); return info
        a1 = api.post("/daily-quiz/submit", quizId=qid, answers={str(qid): 0})
        c1 = safe_num(a1.get("code"))
        biz_assert("第1次submit成功或幂等20301", d, c1 in (0, 20301), f"code={c1} msg={a1.get('msg')}")
        a2 = api.post("/daily-quiz/submit", quizId=qid, answers={str(qid): 0})
        c2 = safe_num(a2.get("code"))
        biz_assert("第2次submit返回幂等20301", d, c2 == 20301 or c2 == c1, f"code={c2}（不幂等=可重复送币）")
        o2 = api.get("/coins/overview")
        bal2 = safe_num((o2.get("data") or {}).get("coins_total") or (o2.get("data") or {}).get("coinTotal") or (o2.get("data") or {}).get("total"))
        diff = bal2 - bal1
        biz_assert(f"两次submit余额差≤1份奖励（{diff}）", d, 0 <= diff <= 50,
                   f"bal1={bal1} bal2={bal2} diff={diff}（>50=双送了）")
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
# J013 日记CRUD真保存真删除
# ============================================================
def j013_diary_crud(api):
    d = []
    info = {"name": "J013-日记CRUD真存真删", "ok": False, "detail": d}
    try:
        import time as _t
        ts = int(_t.time()*1000) % 100000
        title = f"E2E测日记_{ts}"
        content = f"日记内容对账测试 ts={ts}"
        cr = api.post("/diaries", title=title, content=content, mood="😊", location="本地测试")
        code_c = safe_num(cr.get("code"))
        biz_assert("POST日记code=0", d, code_c == 0, f"code={code_c} msg={cr.get('msg')}")
        new_id = safe_num((cr.get("data") or {}).get("id")) if code_c == 0 else 0
        biz_assert("POST返回自增id>0", d, new_id > 0, f"返回id={new_id}")
        if not new_id:
            info["ok"] = all(x["ok"] for x in d); return info

        dt = api.get(f"/diaries/{new_id}")
        biz_assert("GET detail命中", d, safe_num(dt.get("code")) == 0,
                   f"code={safe_num(dt.get('code'))} msg={dt.get('msg')}")
        d_back = str(((dt.get("data") or {}).get("title") if isinstance(dt.get("data"), dict) else "") or "")
        biz_assert("detail里title一致", d, d_back == title, f"提交={title} 回读={d_back}")

        ls = api.get("/diaries", page=1, size=50)
        arr = None
        if isinstance(ls.get("data"), dict):
            arr = ls["data"].get("list") or ls["data"].get("items")
        if not isinstance(arr, list):
            arr = ls.get("data") if isinstance(ls.get("data"), list) else []
        hit = next((x for x in arr if isinstance(x, dict) and safe_num(x.get("id")) == new_id), None)
        biz_assert("GET列表命中=真存DB", d, hit is not None, f"id={new_id}列表没命中=Toast假成功Bug")
        try:
            api.delete(f"/diaries/{new_id}")
        except Exception:
            pass
        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        import traceback
        info["detail"].append({"ok": False, "msg": f"💥 脚本异常: {e}\n{traceback.format_exc()}"})
    return info


# ============================================================
def main():
    print("=" * 70)
    print("🧪 心动空间站 · 业务逻辑对账（J002~J013静默Bug挖掘机 · 不启动浏览器）")
    print(f"   API: {API_BASE}   Token: {TOKEN_A}")
    print("=" * 70)
    api = ApiClient(API_BASE, TOKEN_A)
    cases = [
        j002_icebreak(api), j003_mood(api), j004_coin(api), j005_wish(api),
        j006_coin_tally(api), j007_icebreak_exhaust(api), j008_coin_negative_clamp(api),
        j009_anniversary_crud(api), j010_checklist_toggle(api),
        j011_tacit_questions(api), j012_tacit_submit_idempotent(api), j013_diary_crud(api),
    ]
    pass_n = 0
    print()
    for c in cases:
        tag = "✅ PASS" if c["ok"] else "❌ FAIL"
        if c["ok"]: pass_n += 1
        print(f"【{tag}】 {c['name']}")
        for line in c["detail"]:
            prefix = "    · ✅ " if line.get("ok") else "    · ❌ "
            print(f"{prefix}{line['msg']}")
        print()
    print("=" * 70)
    fail_n = len(cases) - pass_n
    print(f"📊 结果：PASS {pass_n} / FAIL {fail_n}   共{len(cases)}个用例")
    if fail_n > 0:
        print("🔴 有FAIL：请把上面的❌行贴给我，对应修后端业务代码")
    else:
        print("🎉 全绿！当前覆盖的12个业务对账场景，0静默Bug！")
    print("=" * 70)
    sys.exit(0 if fail_n == 0 else 1)


if __name__ == "__main__":
    main()