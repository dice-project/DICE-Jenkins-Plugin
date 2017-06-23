Notes on Jenkins plug-ins
=========================

## Getting started

Learning to develop Jenkins plug-ins is not entirely trivial, because the
plethora of information that is out there tends to be more confusing to the
novices than helpful. But here are a few of our own notes to hopefully speed
the process up.

* The [Extend Jenkins] Wiki page is a central page with all the needed (and
  sometimes unneeded) documents.
* The first visit is the [getting started][Plugin tutorial 1] page, slightly
  misleadingly named "Plugin tutorial". This gives instructions on how to start
  a plug-in from the scratch. The Hello World plugin created by the
  `hpi:create` maven target is already a nice working plug-in. It provides
  a descriptor for configuration of the job-specific settings, and the
  call-back `perform`, which does something when Jenkins invokes the job.
* Then see the [What next] page. It provides a few use cases, the most suitable
  extension points, and in some cases a plugin sample.
* Find inspiraiton and directions from other people's plug-ins such as an
  [AppDynamics plug-in][AppDynamics git repo], which also has a
  [tutorial blog post][AppDynamics tutorial].

[Extend Jenkins]:https://wiki.jenkins-ci.org/display/JENKINS/Extend+Jenkins
[Plugin tutorial 1]:https://wiki.jenkins-ci.org/display/JENKINS/Plugin+tutorial
[What next]:https://wiki.jenkins-ci.org/display/JENKINS/Plugin+Cookbook
[AppDynamics tutorial]:https://blog.codecentric.de/en/2013/02/tutorial-jenkins-plugin-development/
[AppDynamics git repo]:https://github.com/jenkinsci/appdynamics-plugin

### Adding a configuration form

We usually want to give the users ability to configure some parameters in the
build step or post-build step settings of a job. Extend a class from one of the
extension points (e.g., from [What next]), such as `Recorder` for post-build
steps. The class needs to override a `public DescriptorImpl getDescriptor()`
method, which returns a `public static final class` that is decorated as
`@Extension`. This last bit is very important.

In the `resources` sub-folder that mirrors the class's fully qualified name,
create a `config.jelly` script to implement the view of the configuration.

To provide a global configuration of the plug-in, also create a `global.jelly`
script.

### Adding a build report

The goal is to have a link in the navigation sidebar (on the left) of the
build's result that will take the user to a custom view of that build.
[This page][On actions] attempts to clarify this by describing `Action`s.

* Implement a class that implements the `hudson.model.Action` interface. Or
  any of its subinterfaces, or extend an implementing class.
* The `getUrlName` implementation must return a unique subpath. Do not include
  trailing slashes in the UrlName (e.g., use `"dice-report"` instead of
  `"dice-report/"`).
* The link will only appear in the list if the Action was used in the build.
  So to test the view of a newly added action, first trigger a build that will
  store the Action in its `build.xml`.

**Note**: if you **don't** want that an Action in the job to produce a build
report page, then use the `hudson.model.InvisibleAction` class when extending
the Action implementation. 

* To defer implementation of the report to a different class, use a
  `WeakReference` in the `Action` implementation class, and also have this
  class implement `StaplerProxy` to provide the `getTarget()` implementation.
* The side panel appears to the left of the report and contains standard
  Jenkins navigation links. We do not explicitly provide the `sidepanel.jelly`
  but rely on the built-in script. However, the script needs an instance
  of an `AbstractBuild<?. ?>` class (it also works if you cast a
  `hudson.model.Run` instance into `AbstractBuild<?. ?>`). So make sure you
  store it with the `Action` as received in the `perform` method.
* Implement an `AbstractBuild<?, ?> getBuild()` method in the report's
  implementation class.
* Use the `st:include` to include the `sidepanel.jelly`:

<pre>
&lt;j:jelly xmlns:j="jelly:core" xmlns:st="jelly:stapler"
         xmlns:l="/lib/layout"&gt;
  &lt;l:layout norefresh="true"&gt;
    <b>&lt;st:include it="${it.build}" page="sidepanel.jelly"/&gt;</b>
    &lt;l:main-panel&gt;
</pre>

* If Jenkins complains from hell with "No page found 'sidepanel.jelly'", then
  the `it` parameter is probably `null`.

### Adding a project-level report

* At the project (job) level, we a gain add an extension of an `Action` to add
  the links, icons and elements such as graph appear when the user opens a
  job/project in Jenkins.
* When we had only the Freestyle builds where we could override the
  `getProjectAction` method in the recorder class.
* A much more general way for project level actions that do not persist any
  data themselves is to introduce them to the list of available actions via
  an extension such as `TransientActionFactory`.
* The approach from the preceding bullet affects all the projects in Jenkins,
  so the extension has to check if the project actually contains anything that
  makes it reasonable to display links and dashboards.

[On actions]: https://wiki.jenkins-ci.org/display/JENKINS/Action+and+its+family+of+subtypes

## Jenkins Pipeline support

Quick points about migration to the pipelines:

* `AbstractBuild` does not work in this context, because workflow/pipeline
  uses different classes that are descended from `Run`, the same class that
  `AbstractBuild` extends.
* If not needed otherwise, using `Run` instead of `AbstractBuild` actually
  works just nicely.
* A class extending `AbstractStepImpl` will define the step as it can be used
  in the `Jenkinsfile` - the function name, the parameters.
* A class extending `AbstractSynchronousNonBlockingStepExecution` (or any
  of its peers that make sense) implement the actual execution of the step.
  This can reuse the implementation of the previous plugin logic, but see
  the note on the `AbstractBuild` and `Run`.
* Project actions have a similar situation: using `AbstractProject` does not
  work for `org.jenkinsci.plugins.workflow.job.WorkflowJob`. Using `Job` as
  a replacement works just as well.

References:

* [Pipeline plug-in development guide](https://github.com/jenkinsci/pipeline-plugin/blob/893e3484a25289c59567c6724f7ce19e3d23c6ee/DEVGUIDE.md)
* Someone wrote a helpful [blogpost][pipeline blogpost] on migrating to
pipelines.

[pipeline blogpost]: https://jenkins.io/blog/2016/05/25/update-plugin-for-pipeline/

## Notes on using Eclipse

The [getting started][Plugin tutorial 1] document provides a working solution
for converting a maven-based project into one that Eclipse will open. But here
is the problem that appears pretty quickly:

* **ClassName cannot be resolved to a type** with `ClassName` being a valid
  class in the project
* **Only a type can be imported. package.name.ClassName resolves to a package**
  but we do not specify any package named after a class.

The problem is that, by default, Eclipse treats the resources folder as a
source folder. Since that mirrors the folder hierarchy of existing classes
including the parent package path, it thinks that there is also a package named
the same. What helps is to remove the resources from the build path:

* In the Package explorer, right-click on the `src/main/resources` node.
* Select **Build Path** -> **Remove from Build Path**
