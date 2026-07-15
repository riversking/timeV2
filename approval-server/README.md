1（entity）→ 2（repository，这时就可以跑起来验证数据库连通）→ 3（DSL model POJO）→ 4（事件模型+EventBus，单元测试验证事件发布订阅）→
5（NodeHandler，一个一个实现，网关最后）→ 6（FlowExecutor，集成联调）→ 7（TaskService）→ 8（Controller）。这样每一步都有东西可测