import {Component, OnInit} from '@angular/core';
import {Workflow} from "../../entity/workflow/workflow";
import {WorkflowService} from "../../service/workflow.service";
import {ActivatedRoute} from "@angular/router";
import {ParamMap} from "@angular/router/src/shared";
import {HttpErrorResponse} from "@angular/common/http";
import {NotificationService} from "../../service/notification.service";
import {ServerSentEventsWorkflowService} from "../../service/server-sent-events-workflow.service";
import {Task} from "../../entity/task/task";
import {Subscription} from "rxjs";
import {SseError} from "../../entity/sse/sse-error";
import {SseErrorType} from "../../entity/sse/sse-error-type";

@Component({
  selector: 'wt-workflow-detail',
  templateUrl: './workflow-detail.component.html',
  styleUrls: ['./workflow-detail.component.scss']
})
export class WorkflowDetailComponent implements OnInit {

  workflow: Workflow;
  private liveEventsSubscription: Subscription;

  constructor(private workflowService: WorkflowService,
              private serverSentEventsWorkflowService: ServerSentEventsWorkflowService,
              private notificationService: NotificationService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.route.paramMap.subscribe((params: ParamMap) => {
      this.unsubscribeFromWorkflowLiveEvents();

      console.log('Getting params');
      const workflowId: string = params.get('id');
      this.fetchWorkflow(workflowId);
    });
  }

  private fetchWorkflow(workflowId: string | number): void {
    console.log(`Fetching workflow ${workflowId}`);

    this.workflowService.getWorkflow(workflowId, true).subscribe(
      (workflow: Workflow) => this.reactToWorkflowReceived(workflow),
      (error: HttpErrorResponse) => {
        if (error.status === 404) {
          this.notificationService.showErrorNotification("Workflow doesn't exist");
        }
      }
    )
  }

  private reactToWorkflowReceived(workflow: Workflow): void {
    this.workflow = workflow;
    this.workflowService.fetchTasks(workflow).subscribe(
      () => {
        if (workflow.isStarted) {
          this.subscribeToWorkflowLiveEvents(workflow);
        }
      }
    );
  }

  private subscribeToWorkflowLiveEvents(workflow: Workflow): void {
    this.liveEventsSubscription = this.serverSentEventsWorkflowService.connect(workflow).subscribe(
      (data: Workflow | Task) => {
        console.log(`Live event data received from workflow ${workflow.data.workflowId}`, data);
        this.reactToEvent(data);
      },
      (error: SseError) => {
        console.log(`Live event error received from workflow ${workflow.data.workflowId}`, error);
        this.reactToErrorEvent(error);
      }
    );
  }

  private unsubscribeFromWorkflowLiveEvents(): void {
    if (this.liveEventsSubscription) {
      this.liveEventsSubscription.unsubscribe();
      this.liveEventsSubscription = null;
    }
  }

  private reactToEvent(data: Workflow | Task): void {
    if (data instanceof Workflow) {
      this.reactToWorkflowEvent(data);
      this.unsubscribeFromWorkflowLiveEvents();
    } else if (data instanceof Task) {
      this.reactToTaskEvent(data);
    }
  }

  private reactToWorkflowEvent(workflow: Workflow): void {
    this.workflowService.updateWorkflow(workflow, this.workflow);
    this.workflow = workflow;
  }

  private reactToTaskEvent(task: Task): void {
    this.workflowService.updateTask(task, this.workflow);
  }

  private reactToErrorEvent(error: SseError): void {
    this.notificationService.showErrorNotification(error.message);
  }

}
