#!groovy

node {
  print("env.JOB_BASE_NAME: " + env.JOB_BASE_NAME)
  print("REPOFORGE_SPECDIR: " + REPOFORGE_SPECDIR)
  print("REPOFORGE_SPECNAME: " + REPOFORGE_SPECNAME)
  def REPOFORGE_FULLSPECNAME = REPOFORGE_SPECDIR + "/" + REPOFORGE_SPECNAME
  print("REPOFORGE_FULLSPECNAME: " + REPOFORGE_FULLSPECNAME)
  print("REPOFORGE_SPECENV: " + REPOFORGE_SPECENV)
  print("REPOFORGE_DISTTAG: " + REPOFORGE_DISTTAG)
  def REPOFORGE_SPECSDIR = "/srv/rpmbuild/repoforge/rpms/specs/"
  def REPOFORGE_BINARIESDIR = "/srv/rpmbuild/repoforge/binaries/"
  def REPOFORGE_TESTSDIR = "/srv/rpmbuild/repoforge/repoforge-t_functional/tests"
  def REPOFORGE_HAS_CONFLICT_DECLARED = false

  stage('BinariesCheck') {
    // check if sources are available
    // TODO check if the sha1sum matches for the sources
    def GITVERSION = sh returnStdout: true, script: "cd " + REPOFORGE_SPECSDIR + "; git log " + REPOFORGE_FULLSPECNAME + " | head -1 | sed 's/^commit //g;'"
    GITVERSION = GITVERSION.trim()
    print("GITVERSION: " + GITVERSION)
    def MYBINDIR = REPOFORGE_BINARIESDIR + REPOFORGE_FULLSPECNAME + "/" + GITVERSION
    print("MYBINDIR: " + MYBINDIR)
    def bindirFile = new java.io.File(MYBINDIR)
    if (!bindirFile.exists()) {
      print "Build failed because the directory for the binaries does not exist: " + MYBINDIR
      buildFailure();
      return 1;
    }
    if (!bindirFile.isDirectory()) {
      print "Build failed because the directory for the binaries is not a directory: " + MYBINDIR
      buildFailure();
      return 1;
    }
    if (bindirFile.list().length == 0) {
      print "Build failed because the directory for the binaries is empty: " + MYBINDIR
      buildFailure();
      return 1;
    }
  }
  stage('ConflictsPreCheck') {
    // check if a conflict with CentOS base is declared in the spec file
    def conflictsMatchString = "^### " + REPOFORGE_DISTTAG.toUpperCase() + " ships with "
    def retval = sh returnStatus: true, script: "grep -q -F \"" + conflictsMatchString + "\" " + REPOFORGE_SPECSDIR + REPOFORGE_FULLSPECNAME
    REPOFORGE_HAS_CONFLICT_DECLARED = (retval == 0)
    print "Match for conflict with returned: " + retval + " and REPOFORGE_HAS_CONFLICT_DECLARED set to " + REPOFORGE_HAS_CONFLICT_DECLARED
  }
  stage('BuildForAllRepo') {
    sh "sudo /srv/rpmbuild/scripts/run-single-build-from-jenkins.pl " + REPOFORGE_SPECDIR + " " + REPOFORGE_SPECNAME + " " + REPOFORGE_SPECENV + " " + /REPOFORGE_DISTTAG + " ALL"
  }
  stage('Results') {
    archiveArtifacts "build.log,builddeps.log,*/*.rpm"
  }
  stage('ConflictsCheck') {
    def overalRetval = 0
    print "starting ConflictsCheck"
    def myCurrentBuild = currentBuild
    def artifactsDir = myCurrentBuild.rawBuild.getArtifactsDir()
    print "artifactsDir: " + artifactsDir
    def scriptCommand = "BASEPROVIDESFILE=/srv/rpmbuild/rpm-build-scripts/conflicts-scripts/" + REPOFORGE_SPECENV + "-base-provides /srv/rpmbuild/rpm-build-scripts/conflicts-scripts/check-multiple-rpms-for-conflicts-with-centos-base.sh /\"" + artifactsDir + "\""
    print "scriptCommand: " + scriptCommand
    overalRetval = sh returnStatus: true, script: scriptCommand
    print "after sh"
    if (overalRetval > 0) {
      if (REPOFORGE_HAS_CONFLICT_DECLARED) {
        print "Conflicts were detected but a conflict was declared in the spec file so it's OK"
      } else {
        print "Conflicts were detected but no conflict was declared in the spec file => ERROR"
	buildFailure()
	return 1
      }
    } else {
      print "No conflicts with base were detected"
    }
  }
  stage('PromoteToAllRepo') {
    // TODO add to 'all' repo
  }
  stage('Tests') {
    def MYTESTSDIR = REPOFORGE_TESTSDIR + "/p_" + REPOFORGE_SPECDIR
    def testsdirFile = new java.io.File(MYTESTSDIR)
    if (!testsdirFile.exists()) {
      print "Tests failed because the directory for the tests does not exist: " + MYTESTSDIR
      buildFailure();
      return 1;
    }
    if (!testsdirFile.isDirectory()) {
      print "Tests failed because the directory for the tests is not a directory: " + MYTESTSDIR
      buildFailure();
      return 1;
    }
    if (testsdirFile.list().length == 0) {
      print "Tests failed because the directory for the tests is not a directory: " + MYTESTSDIR
      buildFailure();
      return 1;
    }
    sh returnStatus: true, script: "sudo /srv/rpmbuild/scripts/run-single-test-from-jenkins.pl " + /REPOFORGE_SPECDIR + " " + REPOFORGE_SPECNAME + " " +  /REPOFORGE_SPECENV + " " + REPOFORGE_DISTTAG
    junit 'test-results/*.xml'	
    print "currentbuild result afters tests is now: " + currentBuild.result
    if (currentBuild.result != null) {
      print "Not continuing because currentBuild.result set, which means the junit tests failed"
      exit 0
    }
  }
  stage('PromoteToTestedRepo') {
    // TODO add to 'tested' repo
  }
  stage('Conflicts') {
    print "Checking conflicts..."
    // TODO check if there is a conflict with base repo
  }
  stage('PromoteToTestedNoConflictsRepo') {
    // TODO add to 'tested' repo
  }
}
