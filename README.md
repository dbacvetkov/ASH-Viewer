![screenshot](screenshots/pash-viewer-screenshot-01.png?raw=true "Screenshot")
				
				PASH Viewer
				
  What is it?
  -----------
  PostgreSQL ASH Viewer (PASH Viewer) provides graphical view of active session history data within the PostgreSQL database.

  PASH Viewer provides graphical Top Activity, similar Top Activity analysis of Oracle Enterprise Manager performance page.
  PASH Viewer store ASH data locally using embedded database Oracle Berkeley DB Java Edition.
  The default capture rate is one snapshot every 1 seconds.
  PASH Viewer support PostgreSQL 10 (and 9.0, 9.4 - 9.6 with limited functions).
  
  Read how it works: https://github.com/dbacvetkov/PASH-Viewer/wiki

  Use superuser (postgres) to connect to DB, if you want to see query plans.
  Or use your special user for monitoring:

    CREATE USER pgmonuser WITH password 'pgmonuser';
    GRANT pg_monitor TO pgmonuser;


  System Requirements
  -------------------
  JDK 1.7+


  Building PASH Viewer
  ----------------
  1) Run ./gradlew assembleDist (gradlew.bat for Windows)

  2) Binary archive will be created in build/distributions directory


  Running PASH Viewer
  ----------------
  1) Unpack the archive, eg:
      unzip PASH-Viewer-0.3.zip

  2) A directory called "PASH-Viewer-0.3" will be created.

  3) Make sure JAVA_HOME is set to the location of your JDK, 
  	  see run.cmd/run.sh (on Windows/Unix platform).

  4) Run bin/PASH-Viewer.bat or bin/PASH-Viewer (on Window/Unix).


   Licensing
   ---------
   Please see the file called LICENSE


   PASH Viewer URL
   ----------
   https://github.com/dbacvetkov/PASH-Viewer
