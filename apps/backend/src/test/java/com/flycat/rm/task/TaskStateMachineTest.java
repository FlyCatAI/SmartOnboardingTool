package com.flycat.rm.task;

import com.flycat.rm.common.statemachine.IllegalTransitionException;
import com.flycat.rm.common.statemachine.StateMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 task-management/spec.md 中 5 个 Scenario 描述的合法/非法流转。
 * 测试用例命名直接对应 spec Scenario 标题，便于回溯。
 */
class TaskStateMachineTest {

    private final StateMachine<TaskStatus, TaskEvent> sm = TaskStateMachineFactory.create();

    @Test
    void scenario_主管派单创建任务的合法流转() {
        // 主管派单时直接指定负责人——初始态由 service 层置为 PENDING_HANDLE（跳过 DISPATCH 事件）。
        TaskStatus s = TaskStatus.PENDING_HANDLE;
        s = sm.fire(s, TaskEvent.START_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, s);
        s = sm.fire(s, TaskEvent.SUBMIT_COMPLETION);
        assertEquals(TaskStatus.PENDING_CONFIRM, s);
        s = sm.fire(s, TaskEvent.CONFIRM_COMPLETION);
        assertEquals(TaskStatus.DONE, s);
        assertTrue(sm.isTerminal(s));
    }

    @Test
    void scenario_退回完成回报回到处理中() {
        TaskStatus s = TaskStatus.PENDING_CONFIRM;
        s = sm.fire(s, TaskEvent.REJECT_COMPLETION);
        assertEquals(TaskStatus.IN_PROGRESS, s);
        // 退回后负责人可再次提交
        s = sm.fire(s, TaskEvent.SUBMIT_COMPLETION);
        assertEquals(TaskStatus.PENDING_CONFIRM, s);
    }

    @Test
    void scenario_主管草稿创建任务的合法流转() {
        TaskStatus s = TaskStatus.PENDING_DISPATCH;
        TaskStatus byDispatch = sm.fire(s, TaskEvent.DISPATCH);
        assertEquals(TaskStatus.PENDING_HANDLE, byDispatch);

        TaskStatus byPool = sm.fire(s, TaskEvent.PUBLISH_TO_POOL);
        assertEquals(TaskStatus.PENDING_CLAIM, byPool);
    }

    @Test
    void scenario_任务池模式_认领后汇合到待处理() {
        TaskStatus s = TaskStatus.PENDING_CLAIM;
        s = sm.fire(s, TaskEvent.CLAIM);
        assertEquals(TaskStatus.PENDING_HANDLE, s);
    }

    @Test
    void scenario_任务关闭_任意非终态() {
        for (TaskStatus from : new TaskStatus[]{
                TaskStatus.PENDING_DISPATCH,
                TaskStatus.PENDING_CLAIM,
                TaskStatus.PENDING_HANDLE,
                TaskStatus.IN_PROGRESS,
                TaskStatus.PENDING_CONFIRM
        }) {
            assertEquals(TaskStatus.CLOSED, sm.fire(from, TaskEvent.CLOSE),
                    "CLOSE from " + from + " should reach CLOSED");
        }
    }

    @Test
    void scenario_非法状态流转_终态拒绝再变更() {
        for (TaskStatus terminal : new TaskStatus[]{TaskStatus.DONE, TaskStatus.CLOSED}) {
            for (TaskEvent event : TaskEvent.values()) {
                IllegalTransitionException ex = assertThrows(IllegalTransitionException.class,
                        () -> sm.fire(terminal, event),
                        "terminal " + terminal + " should reject " + event);
                assertEquals("terminal_state", ex.reason());
            }
        }
    }

    @Test
    void scenario_非法状态流转_未声明的转换被拒绝() {
        assertThrows(IllegalTransitionException.class,
                () -> sm.fire(TaskStatus.PENDING_HANDLE, TaskEvent.SUBMIT_COMPLETION));
        assertThrows(IllegalTransitionException.class,
                () -> sm.fire(TaskStatus.IN_PROGRESS, TaskEvent.CLAIM));
        assertThrows(IllegalTransitionException.class,
                () -> sm.fire(TaskStatus.PENDING_CLAIM, TaskEvent.START_PROGRESS));
    }

    @Test
    void canFire_only_true_for_declared_transitions() {
        assertTrue(sm.canFire(TaskStatus.PENDING_HANDLE, TaskEvent.START_PROGRESS));
        assertFalse(sm.canFire(TaskStatus.PENDING_HANDLE, TaskEvent.SUBMIT_COMPLETION));
        assertFalse(sm.canFire(TaskStatus.DONE, TaskEvent.CLOSE));
    }
}
