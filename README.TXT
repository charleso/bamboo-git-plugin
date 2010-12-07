You have successfully created a plugin using the Bamboo plugin archetype!

Here are the SDK commands you'll use immediately:

* atlas-run   -- installs this plugin into Bamboo and starts it on http://localhost:6990/bamboo
* atlas-debug -- same as atlas-run, but allows a debugger to attach at port 5005
* atlas-cli   -- after atlas-run or atlas-debug, opens a Maven command line window:
                 - 'pi' reinstalls the plugin into the running Bamboo instance
* atlas-help  -- prints description for all commands in the SDK

Full documentation is always available at:

http://confluence.atlassian.com/display/DEVNET/Developing+your+Plugin+using+the+Atlassian+Plugin+SDK

----

To avoid "NoClassDefinitionFound" Exception copy JGit library manually to your target directory:

cp /home/pstefaniak/.m2/repository/org/eclipse/jgit/org.eclipse.jgit/0.9.3/org.eclipse.jgit-0.9.3.jar target/bamboo/webapp/WEB-INF/lib/

