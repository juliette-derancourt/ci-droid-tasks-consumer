package com.societegenerale.cidroid.tasks.consumer.infrastructure;

import com.societegenerale.cidroid.tasks.consumer.infrastructure.config.GlobalProperties;
import com.societegenerale.cidroid.tasks.consumer.services.RemoteGitHub;
import com.societegenerale.cidroid.tasks.consumer.services.exceptions.BranchAlreadyExistsException;
import com.societegenerale.cidroid.tasks.consumer.services.exceptions.GitHubAuthorizationException;
import com.societegenerale.cidroid.tasks.consumer.services.model.github.*;
import feign.*;
import feign.codec.ErrorDecoder;
import feign.httpclient.ApacheHttpClient;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static feign.FeignException.errorStatus;

@FeignClient(name = "github", url = "${gitHub.api.url}", decode404 = true, configuration = RemoteGitHubConfig.class)
public interface FeignRemoteGitHub extends RemoteGitHub {

    Map<String, String> bodyToClosePR = Collections.singletonMap("state", "closed");

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/pulls?state=open",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    List<PullRequest> fetchOpenPullRequests(@PathVariable("repoFullName") String repoFullName);

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/pulls/{prNumber}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    PullRequest fetchPullRequestDetails(@PathVariable("repoFullName") String repoFullName,
                                        @PathVariable("prNumber") int prNumber);

    @RequestMapping(method = RequestMethod.GET,
                    value = "/users/{login}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    User fetchUser(@PathVariable("login") String login);

    @RequestMapping(method = RequestMethod.POST,
                    value = "/repos/{repoFullName}/issues/{prNumber}/comments",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    void addCommentOnPR(@PathVariable("repoFullName") String repoFullName,
                                    @PathVariable("prNumber") int prNumber,
                                    @RequestBody Comment comment);

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/pulls/{prNumber}/files",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    List<PullRequestFile> fetchPullRequestFiles(@PathVariable("repoFullName") String repoFullName,
                                                @PathVariable("prNumber") int prNumber);

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/issues/{prNumber}/comments",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    List<PullRequestComment> fetchPullRequestComments(@PathVariable("repoFullName") String repoFullName,
                                                      @PathVariable("prNumber") int prNumber);


    @Override
    default UpdatedResource deleteContent(String repoFullName, String path, DirectCommit directCommit, String oauthToken)
            throws GitHubAuthorizationException {

        return buildContentClient(repoFullName, path, oauthToken).deleteResource(directCommit);
    }


    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/contents/{path}?ref={branch}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    ResourceContent fetchContent(@PathVariable("repoFullName") String repoFullName,
                                 @PathVariable("path") String path,
                                 @PathVariable("branch") String branch);


    @Override
    default UpdatedResource updateContent(String repoFullName, String path, DirectCommit directCommit, String oauthToken) throws
            GitHubAuthorizationException {

        return buildContentClient(repoFullName, path, oauthToken).updateContent(directCommit);

    }

    static ContentClient buildContentClient(String repoFullName, String path, String oauthToken) {
        return Feign.builder()
                .logger(new Slf4jLogger(ContentClient.class))
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new UpdateContentErrorDecoder())
                .requestInterceptor(new OAuthInterceptor(oauthToken))
                .logLevel(Logger.Level.FULL)
                .target(ContentClient.class, GlobalProperties.getGitHubApiUrl() + "/repos/" + repoFullName + "/contents/" + path);
    }

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    Optional<Repository> fetchRepository(@PathVariable("repoFullName") String repoFullName);

    @RequestMapping(method = RequestMethod.GET,
                    value = "/repos/{repoFullName}/git/refs/heads/{branchName}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    Reference fetchHeadReferenceFrom(@PathVariable("repoFullName") String repoFullNameString, @PathVariable("branchName") String branchName);

    @Override
    default Reference createBranch(String repoFullName, String branchName, String fromReferenceSha1, String oauthToken)
            throws BranchAlreadyExistsException, GitHubAuthorizationException {

        GitReferenceClient gitReferenceClient = GitReferenceClient.buildGitReferenceClient(oauthToken)
                .target(GitReferenceClient.class, GlobalProperties.getGitHubApiUrl() + "/repos/" + repoFullName + "/git/refs");

        return gitReferenceClient.createBranch(new InputRef("refs/heads/" + branchName, fromReferenceSha1));
    }

    @Override
    default User fetchCurrentUser(String oAuthToken) {

        GitReferenceClient gitReferenceClient = GitReferenceClient.buildGitReferenceClient(oAuthToken)
                .target(GitReferenceClient.class, GlobalProperties.getGitHubApiUrl() + "/user");

        return gitReferenceClient.getCurrentUser();

    }

    @Override
    default PullRequest createPullRequest(String repoFullName, PullRequestToCreate newPr, String oauthToken)
            throws GitHubAuthorizationException {

        GitReferenceClient gitReferenceClient = GitReferenceClient.buildGitReferenceClient(oauthToken)
                .target(GitReferenceClient.class, GlobalProperties.getGitHubApiUrl() + "/repos/" + repoFullName + "/pulls");

        return gitReferenceClient.createPullRequest(newPr);
    }

    @Override
    default void closePullRequest(String repoFullName, int prNumber) {
        updatePullRequest(repoFullName, prNumber, bodyToClosePR);
    }

    @RequestMapping(method = RequestMethod.PATCH,
                    value = "/repos/{repoFullName}/pulls/{prNumber}",
                    consumes = MediaType.APPLICATION_JSON_VALUE,
                    produces = MediaType.APPLICATION_JSON_VALUE)
    void updatePullRequest(@PathVariable("repoFullName") String repoFullName,
                           @PathVariable("prNumber") int prNumber,
                           @RequestBody Map<String, String> body);

    @Data
    @AllArgsConstructor
    class InputRef {

        private String ref;

        private String sha;

    }
}

@Configuration
class RemoteGitHubConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    RequestInterceptor oauthTokenSetterInterceptor(@Value("${gitHub.oauthToken:#{null}}") String oauthToken) {
        return new OAuthInterceptor(oauthToken);
    }

    /**
     * adding an ApacheHttpClient to enable PATCH requests with Feign
     */
    @Bean
    Client apacheHttpClient() {
        return new ApacheHttpClient();
    }

}

interface ContentClient {

    @RequestLine("PUT")
    @Headers("Content-Type: application/json")
    UpdatedResource updateContent(DirectCommit directCommit) throws GitHubAuthorizationException;

    @RequestLine("DELETE")
    @Headers("Content-Type: application/json")
    UpdatedResource deleteResource(DirectCommit directCommit) throws GitHubAuthorizationException;
}

interface GitReferenceClient {

    @RequestLine("POST")
    @Headers("Content-Type: application/json")
    Reference createBranch(FeignRemoteGitHub.InputRef inputRef) throws BranchAlreadyExistsException, GitHubAuthorizationException;

    @RequestLine("POST")
    @Headers("Content-Type: application/json")
    PullRequest createPullRequest(PullRequestToCreate newPr) throws GitHubAuthorizationException;

    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    User getCurrentUser();

    static Feign.Builder buildGitReferenceClient(String oauthToken) {
        return Feign.builder()
                .logger(new Slf4jLogger(GitReferenceClient.class))
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .errorDecoder(new BranchCreationErrorDecoder())
                .requestInterceptor(new OAuthInterceptor(oauthToken))
                .logLevel(Logger.Level.FULL);
    }
}

class OAuthInterceptor implements RequestInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private String oauthToken;

    public OAuthInterceptor(String OauthToken) {
        this.oauthToken = OauthToken;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {

        requestTemplate.header(AUTHORIZATION_HEADER, "token " + oauthToken);
    }
}

@Slf4j
class BranchCreationErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {

        if (response.status() == 422) {
            return new BranchAlreadyExistsException("Branch or PR seems to already exist : " + response.reason());
        }

        if (response.status() == 401) {
            return new GitHubAuthorizationException("Issue with credentials provided : " + response.reason());
        }

        return errorStatus(methodKey, response);
    }
}

@Slf4j
class UpdateContentErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {

        if (response.status() == 401) {
            return new GitHubAuthorizationException("Issue with credentials provided : " + response.reason());
        }

        return errorStatus(methodKey, response);
    }
}
