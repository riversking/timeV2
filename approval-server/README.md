1（entity）→ 2（repository，这时就可以跑起来验证数据库连通）→ 3（DSL model POJO）→ 4（事件模型+EventBus，单元测试验证事件发布订阅）→
5（NodeHandler，一个一个实现，网关最后 接下来是排他网关和并行网关）→ 6（FlowExecutor，集成联调）→ 7（TaskService）→ 8（Controller）。这样每一步都有东西可测


1.创建流程
/flowDefinition/create
{
"definitionKey": "leave_apply",
"name": "请假审批流程",
"description": "3天内经理审批，超3天总监审批",
"category": "OA",
"icon": "icon-leave",
"definitionJson": "{\"key\":\"leave_apply\",\"name\":\"请假审批流程\",\"nodes\":[{\"id\":\"start\",\"type\":\"START\",\"name\":\"开始\"},{\"id\":\"apply\",\"type\":\"USER_TASK\",\"name\":\"提交请假申请\",\"config\":{\"assignee\":\"u1001\"}},{\"id\":\"gateway\",\"type\":\"EXCLUSIVE_GATEWAY\",\"name\":\"天数判断\"},{\"id\":\"manager\",\"type\":\"USER_TASK\",\"name\":\"经理审批\",\"config\":{\"candidateUsers\":[\"u2001\",\"u2002\"]}},{\"id\":\"director\",\"type\":\"USER_TASK\",\"name\":\"总监审批\",\"config\":{\"candidateUsers\":[\"u3001\"]}},{\"id\":\"end\",\"type\":\"END\",\"name\":\"结束\"}],\"edges\":[{\"id\":\"e1\",\"source\":\"start\",\"target\":\"apply\"},{\"id\":\"e2\",\"source\":\"apply\",\"target\":\"gateway\"},{\"id\":\"e3\",\"source\":\"gateway\",\"target\":\"manager\",\"conditionExpression\":\"#days <= 3\"},{\"id\":\"e4\",\"source\":\"gateway\",\"target\":\"director\",\"conditionExpression\":\"#days > 3\"},{\"id\":\"e5\",\"source\":\"manager\",\"target\":\"end\"},{\"id\":\"e6\",\"source\":\"director\",\"target\":\"end\"}]}",
"loginUser": {
"username": "admin",
"userId": "admin"
}
}