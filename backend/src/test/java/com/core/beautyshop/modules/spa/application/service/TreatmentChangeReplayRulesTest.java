package com.core.beautyshop.modules.spa.application.service;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.Course;
import com.core.beautyshop.modules.spa.domain.treatment.TreatmentEntities.Change;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TreatmentChangeReplayRulesTest {
    Course course() { Course c = new Course(); c.setUserId(10L); c.setPlanRevision(2); c.setPlanJson("accepted snapshot"); return c; }
    Change change() { Change c = new Change(); c.setStatus("APPLIED"); c.setBaseRevision(1); c.setPlanJson("accepted snapshot"); c.setAcceptedBy(10L); c.setApprovedBy(20L); c.setAcceptedAt(Instant.now()); return c; }
    @Test void exactAppliedVersionCanReplayWithoutMutatingTheCourse() {
        assertTrue(TreatmentCourseService.appliedRevisionMatches(course(), change()));
    }
    @Test void oldConsentCannotReplayAfterAnotherVersionOrDifferentContents() {
        Course course = course(); course.setPlanRevision(3); assertFalse(TreatmentCourseService.appliedRevisionMatches(course, change()));
        course.setPlanRevision(2); course.setPlanJson("different snapshot"); assertFalse(TreatmentCourseService.appliedRevisionMatches(course, change()));
    }
    @Test void replayRequiresActualApprovalAndConsentFromTheOwner() {
        Change change = change(); change.setApprovedBy(null); assertFalse(TreatmentCourseService.appliedRevisionMatches(course(), change));
        change = change(); change.setAcceptedAt(null); assertFalse(TreatmentCourseService.appliedRevisionMatches(course(), change));
        change = change(); change.setAcceptedBy(99L); assertFalse(TreatmentCourseService.appliedRevisionMatches(course(), change));
    }
}
