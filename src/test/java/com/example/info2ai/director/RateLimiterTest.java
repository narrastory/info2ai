package com.example.info2ai.director;

import com.example.info2ai.api.DirectorRequests.RunStatus;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class RateLimiterTest {
    @Test
    public void slidesFromEachTimestampAndReleasesSlotsAtExactlySixtySeconds() {
        var limiter = new RateLimiter(2);
        assertTrue(limiter.tryAcquire(1000));
        assertTrue(limiter.tryAcquire(2000));
        assertFalse(limiter.tryAcquire(60999));
        assertEquals(1, limiter.status(60999).resetInSeconds());
        assertEquals(1, limiter.status(61000).remaining());
        assertTrue(limiter.tryAcquire(61000));
        assertEquals(0, limiter.status(61000).remaining());
        assertEquals(1, limiter.status(62000).remaining());
        assertEquals(0, limiter.status(121000).resetInSeconds());
    }

    @Test
    public void chargesOnlyExecutedRolledBackFailedAndTimeout() {
        var limiter = new RateLimiter(4);
        List.of(RunStatus.PARSE_ERROR, RunStatus.REJECTED).forEach(status -> limiter.record(status, 0));
        assertEquals(4, limiter.status(0).remaining());
        List.of(RunStatus.EXECUTED, RunStatus.ROLLED_BACK, RunStatus.FAILED, RunStatus.TIMEOUT)
            .forEach(status -> limiter.record(status, 0));
        assertEquals(0, limiter.status(0).remaining());
        assertEquals(60, limiter.status(0).resetInSeconds());
        assertFalse(limiter.tryAcquire(0));
    }
}
