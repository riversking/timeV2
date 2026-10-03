#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
双部门并行审批「打回重审」E2E 验证脚本（经网关 8006）

场景：
  A. 双通过(600万)：A链串签逐级(ri123→app) + B(666)并行 → Join 2/2 → 管理员 → 老板 → END
  B. 单退重审(600万)：A1退回 → 本节点剩余任务清理(WAITING删除) + B不受影响 → 发起人修改重提
     → A链新节点实例重审通过 → Join → 管理员 → 老板 → END
  C. 双退回(600万)：A退回后 B退回 → 判定网关读双变量 → END + 实例COMPLETED(RETURNED) + 级联清理
  D. 单部门(100万)：无B任务，A链 → 管理员 → 老板
  E. 单部门退回重审(100万)
  F. 存量兼容(legacy 无 returnMode)：正向流转正常；退回=直接终止+清理

用法: python3 e2e_rework.py
"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

GW = "http://localhost:8006"
AP = GW + "/approval-server"
US = GW + "/user-server"
# 测试资产目录（本脚本所在目录，需与 dual_dept_v1.json / legacy_dept_v1.json 同目录）
TMP = os.path.dirname(os.path.abspath(__file__))

_TOKENS = {}


# ==================== HTTP ====================

def http(path, body=None, token=None, timeout=25):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(path, data=data, method="POST" if data is not None else "GET")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return json.loads(raw)
        except Exception:
            return {"code": e.code, "message": raw[:300]}


def ok(resp, what):
    assert isinstance(resp, dict) and resp.get("code") == 200, f"{what} 失败: {resp}"
    return resp.get("data")


def login(uid, pwd=None):
    if uid in _TOKENS:
        return _TOKENS[uid]
    pwd = pwd or ("888888" if uid == "admin" else "123456")
    d = ok(http(US + "/login", {"username": uid, "password": pwd}), f"登录 {uid}")
    tk = d.get("token") or d.get("sessionId")
    assert tk, f"登录 {uid} 无 token: {d}"
    _TOKENS[uid] = tk
    return tk


# ==================== 业务封装 ====================

def list_todo(uid):
    d = ok(http(AP + "/task/listTodo", {"currentPage": 1, "pageSize": 100}, login(uid)),
           f"listTodo {uid}")
    return (d or {}).get("tasks", [])


def list_claimable(uid):
    d = ok(http(AP + "/task/listClaimable", {"currentPage": 1, "pageSize": 100}, login(uid)),
           f"listClaimable {uid}")
    return (d or {}).get("tasks", [])


def find_task(uid, instance_id, name):
    """在待办 + 待认领池中查找匹配任务（CLAIM 模式任务在认领池）"""
    for t in list_todo(uid):
        if (int(t.get("instanceId", 0)) == int(instance_id)
                and t.get("taskName") == name
                and t.get("status") in ("PENDING", "CLAIMED")):
            return t
    for t in list_claimable(uid):
        if (int(t.get("instanceId", 0)) == int(instance_id)
                and t.get("taskName") == name
                and t.get("status") == "PENDING"):
            t["_need_claim"] = True
            return t
    return None


def act(uid, task, action, comment="e2e"):
    if task.get("_need_claim"):
        ok(http(AP + "/task/claim", {
            "taskNo": task["taskNo"],
            "instanceId": str(task["instanceId"]),
            "userId": uid,
        }, login(uid)), f"claim {task['taskNo']}")
        print(f"    · {uid} claim [{task['taskName']}]")
    body = {
        "taskNo": task["taskNo"],
        "comment": comment,
        "instanceId": str(task["instanceId"]),
        "userId": uid,
    }
    ok(http(AP + f"/task/{action}", body, login(uid)), f"{action} {task['taskNo']}")
    print(f"    · {uid} {action} [{task['taskName']}]")


def instance(instance_no):
    d = ok(http(AP + "/flowInstance/getByNo", {"instanceNo": instance_no}, login("admin")),
           "getByNo")
    return d


def start(def_key, title, variables):
    d = ok(http(AP + "/flow/startProcess", {
        "definitionKey": def_key,
        "title": title,
        "businessKey": f"e2e-{int(time.time() * 1000)}",
        "initiator": "testUser",
        "initiatorName": "testUser",
        "variables": json.dumps(variables),
    }, login("testUser")), "startProcess")
    iid, ino = d["instanceId"], d["instanceNo"]
    print(f"  → 发起 {title}: instanceNo={ino} id={iid}")
    return ino, iid


def _latest_definition(key):
    """返回最新版本定义行的 (id, status)，无则 None"""
    row = db(f"SELECT id, status FROM timer_approval.flow_definition "
             f"WHERE definition_key='{key}' AND is_deleted=0 ORDER BY version DESC, id DESC LIMIT 1")
    if not row:
        return None
    parts = row.split("\t")
    return int(parts[0]), parts[1]


def ensure_definition(json_path, key, name):
    with open(json_path, encoding="utf-8") as f:
        dsl_text = f.read()
    dsl = json.loads(dsl_text)
    admin = login("admin")
    cur = _latest_definition(key)
    if cur:
        cur_id, cur_status = cur
        cur_json = db(f"SELECT definition_json FROM timer_approval.flow_definition WHERE id={cur_id}")
        try:
            same = json.loads(cur_json) == dsl
        except Exception:
            same = False
        if same:
            if cur_status == "DRAFT":
                ok(http(AP + "/flowDefinition/publish", {"id": cur_id}, admin), f"publish {key}")
                print(f"  定义 {key} 草稿(id={cur_id})已发布")
            else:
                print(f"  定义 {key} 已发布(id={cur_id})且内容一致，跳过")
            return cur_id
    cr = http(AP + "/flowDefinition/create", {
        "definitionKey": key, "name": name, "description": "E2E 退回重审验证",
        "category": "e2e", "icon": "", "definitionJson": dsl_text,
    }, admin)
    assert cr.get("code") == 200, f"创建定义 {key} 失败: {cr}"
    draft_id, _ = _latest_definition(key)
    ok(http(AP + "/flowDefinition/publish", {"id": draft_id}, admin), f"publish {key}")
    print(f"  定义 {key} 创建并发布 id={draft_id}")
    return draft_id


# ==================== DB / 轮询 / 断言 ====================

def db(sql):
    r = subprocess.run(["mysql", "-uroot", "-pKing9128@958", "-N", "-B", "--raw", "-e", sql],
                       capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"mysql 失败: {r.stderr.strip()}")
    return r.stdout.strip()


def active_task_count(instance_id):
    return int(db(f"SELECT COUNT(*) FROM timer_approval.flow_task WHERE instance_id={instance_id} "
                  f"AND status IN ('PENDING','CLAIMED','WAITING') AND is_deleted=0") or 0)


def wait_for(fn, desc, timeout=25, interval=1.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        v = fn()
        if v:
            return v
        time.sleep(interval)
    raise AssertionError(f"等待超时: {desc}")


def wait_task(uid, instance_id, name, timeout=25):
    return wait_for(lambda: find_task(uid, instance_id, name),
                    f"{uid} 收到 [{name}] (instance={instance_id})", timeout)


def wait_instance(instance_no, status=None, approval=None, timeout=25):
    def check():
        d = instance(instance_no)
        if d and (status is None or d.get("status") == status) \
                and (approval is None or d.get("approvalResult") == approval):
            return d
        return None
    return wait_for(check, f"实例 {instance_no} → status={status} approval={approval}", timeout)


def assert_eq(actual, expected, msg):
    assert actual == expected, f"{msg}: 期望 {expected}, 实际 {actual}"


def assert_no_active(instance_id):
    time.sleep(1.5)  # 级联清理为异步事件驱动，留出窗口
    cnt = active_task_count(instance_id)
    assert_eq(cnt, 0, f"实例 {instance_id} 残留活跃任务数")


def vars600(amount=6000000):
    return {
        "amount": amount,
        "leaderChainA": ["ri123", "app"],
        "leaderChainB": ["666"],
        "adminUser": "admin",
        "bossUser": "HaHaHa",
    }


# ==================== 场景 ====================

def scenario_a():
    """双通过：A链串签 + B并行 → Join → 管理员 → 老板"""
    ino, iid = start("dual_dept_rework_v1", "E2E-A 双通过600万", vars600())
    t_ri = wait_task("ri123", iid, "A部门领导审批")
    t_b = wait_task("666", iid, "B部门领导审批")
    act("ri123", t_ri, "approve", "A1 通过")
    t_app = wait_task("app", iid, "A部门领导审批")   # 串签逐级激活
    act("666", t_b, "approve", "B 通过")
    act("app", t_app, "approve", "A2 通过")        # Join 2/2
    t_admin = wait_task("admin", iid, "管理员审批")
    act("admin", t_admin, "approve")
    t_boss = wait_task("HaHaHa", iid, "老板审批")
    act("HaHaHa", t_boss, "approve")
    wait_instance(ino, status="COMPLETED", approval="APPROVED")
    assert_no_active(iid)
    return f"{ino} COMPLETED/APPROVED"


def scenario_b():
    """单退重审：A1退回 → 清理+隔离 → 发起人重提 → A链重审 → 完整走完"""
    ino, iid = start("dual_dept_rework_v1", "E2E-B 单退重审600万", vars600())
    t_ri = wait_task("ri123", iid, "A部门领导审批")
    act("ri123", t_ri, "return", "A1 退回")
    t_fix = wait_task("testUser", iid, "A部门修改重提")
    # 本节点剩余任务（app 的 WAITING）已被清理
    cnt_a = int(db(f"SELECT COUNT(*) FROM timer_approval.flow_task WHERE instance_id={iid} "
                   f"AND task_name='A部门领导审批' AND status IN ('PENDING','CLAIMED','WAITING') "
                   f"AND is_deleted=0") or 0)
    assert_eq(cnt_a, 0, "退回后 A 节点剩余活跃任务")
    assert not find_task("app", iid, "A部门领导审批"), "app 不应被激活"
    # B 不受影响
    t_b = find_task("666", iid, "B部门领导审批")
    assert t_b, "B 部门任务不应受影响"
    # 实例仍在运行
    inst = instance(ino)
    assert_eq(inst.get("status"), "RUNNING", "退回重审时实例状态")
    act("testUser", t_fix, "approve", "修改后重提")
    t_ri2 = wait_task("ri123", iid, "A部门领导审批")
    ni_cnt = int(db(f"SELECT COUNT(*) FROM timer_approval.flow_node_instance WHERE instance_id={iid} "
                    f"AND node_id='deptA_approval'") or 0)
    assert ni_cnt >= 2, f"重审应创建新节点实例，实际 {ni_cnt}"
    act("ri123", t_ri2, "approve", "重审 A1 通过")
    t_app2 = wait_task("app", iid, "A部门领导审批")
    act("app", t_app2, "approve", "重审 A2 通过")
    act("666", t_b, "approve", "B 通过")           # Join 2/2
    t_admin = wait_task("admin", iid, "管理员审批")
    act("admin", t_admin, "approve")
    t_boss = wait_task("HaHaHa", iid, "老板审批")
    act("HaHaHa", t_boss, "approve")
    wait_instance(ino, status="COMPLETED", approval="APPROVED")
    assert_no_active(iid)
    return f"{ino} 退回重审后 COMPLETED/APPROVED (A节点实例数={ni_cnt})"


def scenario_c():
    """双退回：A退回后 B退回 → 判定网关 → END + 级联清理"""
    ino, iid = start("dual_dept_rework_v1", "E2E-C 双退回600万", vars600())
    t_ri = wait_task("ri123", iid, "A部门领导审批")
    act("ri123", t_ri, "return", "A1 退回")
    wait_task("testUser", iid, "A部门修改重提")     # 确认 A 已进入重审态
    t_b = wait_task("666", iid, "B部门领导审批")
    act("666", t_b, "return", "B 退回")            # 此时 deptA_result=RETURNED
    wait_instance(ino, status="COMPLETED", approval="RETURNED")
    assert_no_active(iid)
    assert not find_task("testUser", iid, "A部门修改重提"), "修改任务应被级联清理"
    return f"{ino} 双退回 COMPLETED/RETURNED 且任务已清理"


def scenario_d():
    """单部门(100万)：无B任务，A链通过 → 管理员 → 老板"""
    ino, iid = start("dual_dept_rework_v1", "E2E-D 单部门100万", vars600(amount=1000000))
    t_ri = wait_task("ri123", iid, "A部门领导审批")
    assert not find_task("666", iid, "B部门领导审批"), "小额不应有 B 任务"
    act("ri123", t_ri, "approve", "A1 通过")
    t_app = wait_task("app", iid, "A部门领导审批")
    act("app", t_app, "approve", "A2 通过")
    t_admin = wait_task("admin", iid, "管理员审批")
    act("admin", t_admin, "approve")
    t_boss = wait_task("HaHaHa", iid, "老板审批")
    act("HaHaHa", t_boss, "approve")
    wait_instance(ino, status="COMPLETED", approval="APPROVED")
    assert_no_active(iid)
    return f"{ino} COMPLETED/APPROVED (无B任务)"


def scenario_e():
    """单部门退回重审(100万)"""
    ino, iid = start("dual_dept_rework_v1", "E2E-E 单退重审100万", vars600(amount=1000000))
    t_ri = wait_task("ri123", iid, "A部门领导审批")
    act("ri123", t_ri, "return", "A1 退回")
    t_fix = wait_task("testUser", iid, "A部门修改重提")
    act("testUser", t_fix, "approve", "修改后重提")
    t_ri2 = wait_task("ri123", iid, "A部门领导审批")
    act("ri123", t_ri2, "approve", "重审 A1 通过")
    t_app2 = wait_task("app", iid, "A部门领导审批")
    act("app", t_app2, "approve", "重审 A2 通过")
    t_admin = wait_task("admin", iid, "管理员审批")
    act("admin", t_admin, "approve")
    t_boss = wait_task("HaHaHa", iid, "老板审批")
    act("HaHaHa", t_boss, "approve")
    wait_instance(ino, status="COMPLETED", approval="APPROVED")
    assert_no_active(iid)
    return f"{ino} 退回重审后 COMPLETED/APPROVED"


def scenario_f():
    """存量兼容：legacy 定义（无 returnMode）"""
    lvars = {"amount": 100000, "leaderChainA": ["ri123"],
             "adminUser": "admin", "bossUser": "HaHaHa"}
    # F1 正向：A通过 → 管理员 → 老板 → END
    ino1, iid1 = start("legacy_dept_v1", "E2E-F1 存量正向", lvars)
    t = wait_task("ri123", iid1, "A部门领导审批")
    act("ri123", t, "approve")
    t = wait_task("admin", iid1, "管理员审批")
    act("admin", t, "approve")
    t = wait_task("HaHaHa", iid1, "老板审批")
    act("HaHaHa", t, "approve")
    wait_instance(ino1, status="COMPLETED", approval="APPROVED")
    assert_no_active(iid1)
    # F2 退回=直接终止 + 清理（无 returnMode）
    ino2, iid2 = start("legacy_dept_v1", "E2E-F2 存量退回", lvars)
    t = wait_task("ri123", iid2, "A部门领导审批")
    act("ri123", t, "return", "退回（存量语义=终止）")
    wait_instance(ino2, status="COMPLETED", approval="RETURNED")
    assert_no_active(iid2)
    return f"F1={ino1} APPROVED; F2={ino2} RETURNED(terminate)"


# ==================== 主流程 ====================

def main():
    print("== 准备定义 ==")
    ensure_definition(f"{TMP}/dual_dept_v1.json", "dual_dept_rework_v1",
                      "金额条件双部门并行审批（打回重审）")
    ensure_definition(f"{TMP}/legacy_dept_v1.json", "legacy_dept_v1",
                      "存量兼容单部门审批")
    scenarios = [
        ("A 双通过(600万)", scenario_a),
        ("B 单退重审(600万)", scenario_b),
        ("C 双退回(600万)", scenario_c),
        ("D 单部门(100万)", scenario_d),
        ("E 单退重审(100万)", scenario_e),
        ("F 存量兼容", scenario_f),
    ]
    passed, failed = [], []
    for name, fn in scenarios:
        print(f"\n== 场景 {name} ==")
        try:
            detail = fn()
            passed.append((name, detail))
            print(f"  ✓ {name}: {detail}")
        except Exception as e:  # noqa: BLE001
            failed.append((name, repr(e)))
            print(f"  ✗ {name}: {e}")
    print("\n==================== 汇总 ====================")
    for name, detail in passed:
        print(f"  PASS  {name}: {detail}")
    for name, err in failed:
        print(f"  FAIL  {name}: {err}")
    print(f"总计: {len(passed)} 通过 / {len(failed)} 失败")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
