/**
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2016, 2018
 */

package org.zowe.jobs.tests;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import org.apache.http.HttpStatus;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.zowe.api.common.connectors.zosmf.exceptions.DataSetNotFoundException;
import org.zowe.api.common.errors.ApiError;
import org.zowe.api.common.exceptions.ZoweApiRestException;
import org.zowe.jobs.model.Job;
import org.zowe.tests.IntegrationTestResponse;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;

//TODO LATER - fix to use RestAssured
public class JobSubmitIntegrationTest extends AbstractJobsIntegrationTest {

    @BeforeClass
    public static void setUpJobDatasetsIfRequired() throws Exception {
        // TODO - fix AbstractDatasetsIntegrationTest.initialiseDatasetsIfNecessary();
    }

    @Test
    public void testSubmitJobByString() throws Exception {
        submitJclStringAndVerifyJob(JOB_IEFBR14);
    }

    private void submitJclStringAndVerifyJob(String fileString) throws Exception {
        ValidatableResponse response = submitJobJclStringFromFile(fileString).then().statusCode(HttpStatus.SC_CREATED);
        verifyJob(response);
    }

    private void verifyJob(ValidatableResponse response) throws Exception {
        Job actual = response.extract().body().as(Job.class);
        String jobName = actual.getJobName();
        String jobId = actual.getJobId();
        Job expected = Job.builder().owner(USER.toUpperCase()).subsystem("JES2").type("JOB").executionClass("A")
            .build();

        // We can't know these values at the moment based on input & timing
        actual.setJobId(null);
        actual.setJobName(null);
        actual.setStatus(null);
        actual.setPhaseName(null);

        assertEquals(actual, expected);

        response.header("Location", endsWith(JOBS_ROOT_ENDPOINT + "/" + jobName + "/" + jobId));
    }

    @Test
    public void testSubmitJobByStringWithBadJcl() throws Exception {
        ApiError expectedError = ApiError.builder().status(org.springframework.http.HttpStatus.BAD_REQUEST)
            .message("Job input was not recognized by system as a job").build();

        submitJobJclString("//Some bad jcl").then().statusCode(expectedError.getStatus().value())
            .contentType(ContentType.JSON).body("status", equalTo(expectedError.getStatus().name()))
            .body("message", equalTo(expectedError.getMessage()));
    }

    @Test
    public void testSubmitJobByStringWithEmptyJcl() throws Exception {
        ApiError expectedError = ApiError.builder().status(org.springframework.http.HttpStatus.BAD_REQUEST)
            .message("Invalid field jcl supplied to object submitJobStringRequest - JCL string can't be empty").build();
        submitJobJclString("").then().statusCode(expectedError.getStatus().value()).contentType(ContentType.JSON)
            .body("status", equalTo(expectedError.getStatus().name()))
            .body("message", equalTo(expectedError.getMessage()));
    }

//     TODO LATER - test submitting other invalid JCL (eg line > 72)

    @Test
    @Ignore("see todo") // TODO - need to make build environment set up dataset
    public void testSubmitJobDataSet() throws Exception {
        String dataSetPath = getTestJclMemberPath(JOB_IEFBR14);
        submitAndVerifySuccessfulJob("'" + dataSetPath + "'");
    }

    @Test
    public void testPostJobInvalidJobDataSet() throws Exception {
        String dataSet = "ATLAS.TEST.JCL(INVALID)";
        ZoweApiRestException expected = new DataSetNotFoundException(dataSet);

        submitJobByFile(dataSet).shouldReturnException(expected);
    }

//    @Test
//    public void testPostJobFromUSS() throws Exception {
//        String submitJobUssPath = USER_DIRECTORY + "/submitJob";
//        createUssFileWithJobIfNecessary(submitJobUssPath);
//        submitAndVerifySuccessfulJob(submitJobUssPath);
//    }

    // TODO - work out better solution?
    static String getTestJclMemberPath(String member) {
        return USER.toUpperCase() + ".TEST.JCL(" + member + ")";
    }

//    private void createUssFileWithJobIfNecessary(String submitJobUssPath) throws Exception {
//        if (getAttributes(submitJobUssPath).getStatus() != HttpStatus.SC_OK) {
//            createFile(submitJobUssPath, null);
//            String jobContent = new String(Files.readAllBytes(Paths.get("testFiles/jobIEFBR14")));
//            updateFileContent(submitJobUssPath, jobContent, null);
//        }
//    }
//
    private void submitAndVerifySuccessfulJob(String fileName) throws Exception {
        submitAndVerifyJob(fileName, "expectedResults/Jobs/JobsResponse.json");
    }

    private void submitAndVerifyJob(String fileString, String expectedResultFilePath) throws Exception {
        verifyJob(submitJobByFile(fileString), expectedResultFilePath);
    }

    private void verifyJob(IntegrationTestResponse submitResponse, String expectedResultFilePath) throws Exception {
        submitResponse.shouldHaveStatusCreated();
        Job actualJob = submitResponse.getEntityAs(Job.class);
        try {
            verifyJobIsAsExpected(expectedResultFilePath, actualJob);
            String expectedLocation = BASE_URL + JOBS_ROOT_ENDPOINT + "/" + actualJob.getJobName() + "/"
                    + actualJob.getJobId();
            submitResponse.shouldHaveLocationHeader(expectedLocation);
        } finally {
            deleteJob(actualJob);
        }
    }
}
