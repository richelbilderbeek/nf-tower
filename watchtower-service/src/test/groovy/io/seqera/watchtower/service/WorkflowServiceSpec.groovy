package io.seqera.watchtower.service

import io.micronaut.test.annotation.MicronautTest
import io.seqera.watchtower.Application
import io.seqera.watchtower.controller.TraceWorkflowRequest
import io.seqera.watchtower.domain.MagnitudeSummary
import io.seqera.watchtower.domain.Workflow
import io.seqera.watchtower.pogo.enums.WorkflowStatus
import io.seqera.watchtower.pogo.exceptions.NonExistingWorkflowException
import io.seqera.watchtower.util.AbstractContainerBaseSpec
import io.seqera.watchtower.util.TracesJsonBank
import spock.lang.IgnoreRest

import javax.inject.Inject

@MicronautTest(application = Application.class)
class WorkflowServiceSpec extends AbstractContainerBaseSpec {

    @Inject
    WorkflowService workflowService


    void "start a workflow given a started trace"() {
        given: "a workflow JSON started trace"
        TraceWorkflowRequest workflowStartedTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, null, WorkflowStatus.STARTED)

        when: "unmarshall the JSON to a workflow"
        Workflow workflow
        Workflow.withNewTransaction {
            workflow = workflowService.processWorkflowJsonTrace(workflowStartedTraceJson)
        }

        then: "the workflow has been correctly saved"
        workflow.id
        workflow.checkIsStarted()
        workflow.submit
        !workflow.complete
        Workflow.count() == 1
    }

    void "start a workflow given a started trace, then complete the workflow given a succeeded trace"() {
        given: "a workflow JSON started trace"
        TraceWorkflowRequest workflowStartedTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, null, WorkflowStatus.STARTED)

        when: "unmarshall the JSON to a workflow"
        Workflow workflowStarted
        Workflow.withNewTransaction {
            workflowStarted = workflowService.processWorkflowJsonTrace(workflowStartedTraceJson)
        }

        then: "the workflow has been correctly saved"
        workflowStarted.id
        workflowStarted.checkIsStarted()
        workflowStarted.submit
        !workflowStarted.complete

        when: "given a workflow succeeded trace, unmarshall the succeeded JSON to a workflow"
        TraceWorkflowRequest workflowSucceededTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, workflowStarted.id, WorkflowStatus.SUCCEEDED)
        Workflow workflowSucceeded
        Workflow.withNewTransaction {
            workflowSucceeded = workflowService.processWorkflowJsonTrace(workflowSucceededTraceJson)
        }

        then: "the workflow has been completed"
        workflowStarted.id == workflowSucceeded.id
        workflowSucceeded.checkIsSucceeded()
        workflowSucceeded.submit
        workflowSucceeded.complete
        Workflow.count() == 1

        and: "there is summary info"
//        workflowSucceeded.magnitudeSummaries.size() == 5
//        workflowSucceeded.magnitudeSummaries.taskLabel.every { it == 'sayHello' }
//        workflowSucceeded.magnitudeSummaries.name as Set == ['cpu', 'time', 'reads', 'writes', 'cpuUsage'] as Set
//        MagnitudeSummary.count() == 5

        and: "the trace has progress info"
        workflowSucceededTraceJson.progress.running == 0
        workflowSucceededTraceJson.progress.submitted == 0
        workflowSucceededTraceJson.progress.failed == 0
        workflowSucceededTraceJson.progress.pending == 0
        workflowSucceededTraceJson.progress.succeeded == 4
        workflowSucceededTraceJson.progress.cached == 0
    }

    void "start a workflow given a started trace, then complete the workflow given a failed trace"() {
        given: "a workflow JSON started trace"
        TraceWorkflowRequest workflowStartedTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, null, WorkflowStatus.STARTED)

        when: "unmarshall the JSON to a workflow"
        Workflow workflowStarted
        Workflow.withNewTransaction {
            workflowStarted = workflowService.processWorkflowJsonTrace(workflowStartedTraceJson)
        }

        then: "the workflow has been correctly saved"
        workflowStarted.id
        workflowStarted.checkIsStarted()
        workflowStarted.submit
        !workflowStarted.complete
        Workflow.count() == 1

        when: "given a workflow failed trace, unmarshall the failed JSON to a workflow"
        TraceWorkflowRequest workflowFailedTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, workflowStarted.id, WorkflowStatus.FAILED)
        Workflow workflowFailed
        Workflow.withNewTransaction {
            workflowFailed = workflowService.processWorkflowJsonTrace(workflowFailedTraceJson)
        }

        then: "the workflow has been completed"
        workflowStarted.id == workflowFailed.id
        workflowFailed.checkIsFailed()
        workflowFailed.submit
        workflowFailed.complete
        Workflow.count() == 1

        and: "there is summary info"
//        workflowFailed.magnitudeSummaries.size() == 5
//        workflowFailed.magnitudeSummaries.taskLabel.every { it == 'sayHello' }
//        workflowFailed.magnitudeSummaries.name as Set == ['cpu', 'time', 'reads', 'writes', 'cpuUsage'] as Set
//        MagnitudeSummary.count() == 5
    }

    void "start a workflow given a started trace, then try to start the same one"() {
        given: "a workflow JSON started trace"
        TraceWorkflowRequest workflowStarted1TraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, null, WorkflowStatus.STARTED)

        when: "unmarshall the JSON to a workflow"
        Workflow workflowStarted1
        Workflow.withNewTransaction {
            workflowStarted1 = workflowService.processWorkflowJsonTrace(workflowStarted1TraceJson)
        }

        then: "the workflow has been correctly saved"
        workflowStarted1.id
        workflowStarted1.checkIsStarted()
        workflowStarted1.submit
        !workflowStarted1.complete
        Workflow.count() == 1

        when: "given a workflow started trace with the same workflowId, unmarshall the started JSON to a second workflow"
        TraceWorkflowRequest workflowStarted2TraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, workflowStarted1.id, WorkflowStatus.STARTED)
        Workflow workflowStarted2
        Workflow.withNewTransaction {
            workflowStarted2 = workflowService.processWorkflowJsonTrace(workflowStarted2TraceJson)
        }

        then: "the second workflow is treated as a new one, and sessionId/runName combination cannot be repeated"
        workflowStarted2.errors.getFieldError('sessionId').code == 'unique'
        Workflow.count() == 1
    }

    void "try to start a workflow given a started trace without sessionId"() {
        given: "a workflow JSON started trace without sessionId"
        TraceWorkflowRequest workflowStartedTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, null, WorkflowStatus.STARTED)
        workflowStartedTraceJson.workflow.sessionId = null

        when: "unmarshall the JSON to a workflow"
        Workflow workflowStarted
        Workflow.withNewTransaction {
            workflowStarted = workflowService.processWorkflowJsonTrace(workflowStartedTraceJson)
        }

        then: "the workflow has validation errors"
        workflowStarted.hasErrors()
        workflowStarted.errors.getFieldError('sessionId').code == 'nullable'
        Workflow.count() == 0
    }

    void "try to complete a workflow given a succeeded trace for a non existing workflow"() {
        given: "a workflow JSON started trace"
        TraceWorkflowRequest workflowSucceededTraceJson = TracesJsonBank.extractWorkflowJsonTrace(1, 123, WorkflowStatus.SUCCEEDED)

        when: "unmarshall the JSON to a workflow"
        Workflow workflowSucceeded
        Workflow.withNewTransaction {
            workflowSucceeded = workflowService.processWorkflowJsonTrace(workflowSucceededTraceJson)
        }

        then: "the workflow has been correctly saved"
        thrown(NonExistingWorkflowException)
    }

}
