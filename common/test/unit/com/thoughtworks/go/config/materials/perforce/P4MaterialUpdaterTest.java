/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.config.materials.perforce;

import com.googlecode.junit.ext.JunitExtRunner;
import com.thoughtworks.go.buildsession.BuildSession;
import com.thoughtworks.go.buildsession.BuildSessionBasedTestCase;
import com.thoughtworks.go.domain.JobResult;
import com.thoughtworks.go.domain.materials.RevisionContext;
import com.thoughtworks.go.domain.materials.mercurial.StringRevision;
import com.thoughtworks.go.domain.materials.perforce.P4Client;
import com.thoughtworks.go.domain.materials.perforce.P4Fixture;
import com.thoughtworks.go.domain.materials.perforce.P4MaterialUpdater;
import com.thoughtworks.go.helper.P4TestRepo;
import com.thoughtworks.go.util.TempFiles;
import com.thoughtworks.go.util.TestFileUtil;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static com.thoughtworks.go.util.FileUtil.deleteFolder;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@RunWith(JunitExtRunner.class)

public class P4MaterialUpdaterTest extends BuildSessionBasedTestCase {
    private File workingDir;
    private P4TestRepo repo;
    private P4Fixture p4Fixture;
    private P4Client p4;
    private TempFiles tmpFiles;


    @Before
    public void setup() throws Exception {
        repo = P4TestRepo.createP4TestRepo();
        repo.onSetup();
        p4Fixture = new P4Fixture();
        p4Fixture.setRepo(this.repo);
        workingDir = TestFileUtil.createTempFolder("p4Client");
        if (workingDir == null) {
            throw new RuntimeException();
        }
        p4 = p4Fixture.createClient();
        tmpFiles = new TempFiles();
    }


    @After
    public void teardown() throws Exception {
        p4Fixture.stop(p4);
        deleteFolder(workingDir);
        tmpFiles.cleanUp();
    }

    private final StringRevision REVISION_1 = new StringRevision("1");
    private final StringRevision REVISION_2 = new StringRevision("2");
    private final StringRevision REVISION_3 = new StringRevision("3");
    private static final String VIEW = "//depot/... //something/...";

    @Test
    public void shouldCleanWorkingDir() throws Exception {
        P4Material material = p4Fixture.material(VIEW);
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        File tmpFile = new File(workingDir, "shouldBeDeleted");
        FileUtils.writeStringToFile(tmpFile, "testing");
        assert(tmpFile.exists());
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assert(!tmpFile.exists());
    }

    @Test
    public void shouldSyncToSpecifiedRevision() throws Exception {
        P4Material material = p4Fixture.material(VIEW);
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assertThat(workingDir.listFiles().length, is(7));
        updateTo(material, new RevisionContext(REVISION_3), JobResult.Passed);
        assertThat(workingDir.listFiles().length, is(6));
    }

    @Test
    public void shouldNotDisplayPassword() throws Exception {
        P4Material material = p4Fixture.material(VIEW);
        material.setPassword("wubba lubba dub dub");
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assertThat(console.output(), not(containsString("wubba lubba dub dub")));
        assertThat(console.output(), containsString("setting environment variable 'P4PASSWD' to value '********'"));
    }

    @Test
    public void shouldSetEnvVars() throws Exception {
        P4Material material = p4Fixture.material(VIEW);
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assertThat(console.output(), containsString(format("setting environment variable 'P4PORT' to value '%s'", material.getServerAndPort())));
        assertThat(console.output(), containsString(format("setting environment variable 'P4USER' to value '%s'", material.getUserName())));
        assertThat(console.output(), containsString(format("setting environment variable 'P4CLIENT' to value '%s'", material.clientName(workingDir))));
    }

    @Test
    public void shouldNotFailIfDestDoesNotExist() throws Exception {
        FileUtils.deleteDirectory(workingDir);
        assert(!workingDir.exists());
        P4Material material = p4Fixture.material(VIEW);
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assert(workingDir.exists());
    }

    @Test
    public void shouldSupportCustomDestinations() throws Exception {
        P4Material material = p4Fixture.material(VIEW);
        material.setFolder("dest");
        updateTo(material, new RevisionContext(REVISION_2), JobResult.Passed);
        assertThat(workingDir.listFiles().length, is(1));
        assertThat(new File(workingDir, "dest").listFiles().length, is(7));
    }

    private void updateTo(P4Material material, RevisionContext revisionContext, JobResult expectedResult) {
        BuildSession buildSession = newBuildSession();
        JobResult result = buildSession.build(new P4MaterialUpdater(material).updateTo(workingDir.toString(), revisionContext));
        assertThat(buildInfo(), result, is(expectedResult));
    }
}
