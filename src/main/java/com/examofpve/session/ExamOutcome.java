package com.examofpve.session;

/**
 * 考核结束原因。
 */
public enum ExamOutcome {
    SUCCESS, // 通过
    TIMEOUT, // 超时
    DEATH,   // 死亡
    ABORT,   // 主动终止
    QUIT,    // 退出游戏
    ERROR    // 异常
}
