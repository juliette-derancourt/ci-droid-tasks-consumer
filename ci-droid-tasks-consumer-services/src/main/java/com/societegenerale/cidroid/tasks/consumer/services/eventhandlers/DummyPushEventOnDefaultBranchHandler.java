package com.societegenerale.cidroid.tasks.consumer.services.eventhandlers;

import com.societegenerale.cidroid.tasks.consumer.services.model.GitHubEvent;
import com.societegenerale.cidroid.tasks.consumer.services.model.github.PullRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DummyPushEventOnDefaultBranchHandler implements PushEventOnDefaultBranchHandler {

    @Override
    public void handle(GitHubEvent event, List<PullRequest> pullRequests) {
        log.info("event handled by dummy handler : {}",event);
    }
}
