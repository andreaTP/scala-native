import scala.io.Source
import scala.scalanative.sbtplugin.{ScalaNativePlugin, ScalaNativePluginInternal}
import ScalaNativePlugin.autoImport._

val toolScalaVersion = "2.10.6"

val libScalaVersion  = "2.11.8"

lazy val baseSettings = Seq(
  organization := "org.scala-native",
  version      := scala.scalanative.nir.Versions.current,

  scalafmtConfig := Some(file(".scalafmt"))
)

lazy val toolSettings =
  baseSettings ++ Seq(
    scalaVersion := toolScalaVersion
  )

lazy val libSettings =
  baseSettings ++ ScalaNativePlugin.projectSettings ++ Seq(
    scalaVersion := libScalaVersion,

    nativeEmitDependencyGraphPath := Some(file("out.dot"))
  )

lazy val util =
  project.in(file("util")).
    settings(toolSettings)

lazy val nir =
  project.in(file("nir")).
    settings(toolSettings).
    dependsOn(util)

lazy val tools =
  project.in(file("tools")).
    settings(toolSettings).
    settings(
      libraryDependencies += "commons-io" % "commons-io" % "2.4"
    ).
    dependsOn(nir, util)

lazy val nscplugin =
  project.in(file("nscplugin")).
    settings(toolSettings).
    settings(
      scalaVersion := "2.11.8",
      unmanagedSourceDirectories in Compile ++= Seq(
        (scalaSource in (nir, Compile)).value,
        (scalaSource in (util, Compile)).value
      ),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.scala-lang" % "scala-reflect" % scalaVersion.value
      ),
      publishArtifact in (Compile, packageDoc) := false
    )

lazy val sbtplugin =
  project.in(file("sbtplugin")).
    settings(toolSettings).
    settings(
      sbtPlugin := true
    ).
    dependsOn(tools)

// rt is a library project but it can't use libSettings
// due to the fact that it contains nrt dependency in
// ScalaNativePlugin.projectSettings.
lazy val rtlib =
  project.in(file("rtlib")).
    settings(baseSettings).
    settings(
      scalaVersion := libScalaVersion
    )

lazy val nativelib =
  project.in(file("nativelib")).
    settings(libSettings)

lazy val javalib =
  project.in(file("javalib")).
    settings(libSettings).
    dependsOn(nativelib)

lazy val assembleScalaLibrary = taskKey[Unit](
  "Checks out scala standard library from submodules/scala and then applies overrides.")

lazy val scalalib =
  project.in(file("scalalib")).
    settings(libSettings).
    settings(
      assembleScalaLibrary := {
        import org.eclipse.jgit.api._

        val s = streams.value
        val trgDir = target.value / "scalaSources" / scalaVersion.value

        if (!trgDir.exists) {
          s.log.info(s"Fetching Scala source version ${scalaVersion.value}")

          // Make parent dirs and stuff
          IO.createDirectory(trgDir)

          // Clone scala source code
          new CloneCommand()
            .setDirectory(trgDir)
            .setURI("https://github.com/scala/scala.git")
            .call()
        }

        // Checkout proper ref. We do this anyway so we fail if
        // something is wrong
        val git = Git.open(trgDir)
        s.log.info(s"Checking out Scala source version ${scalaVersion.value}")
        git.checkout().setName(s"v${scalaVersion.value}").call()

        IO.delete(file("scalalib/src/main/scala"))
        IO.copyDirectory(
          (trgDir / "src" / "library" / "scala"),
          file("scalalib/src/main/scala/scala"))

        val epoch :: major :: _ = scalaVersion.value.split("\\.").toList
        IO.copyDirectory(
          file(s"scalalib/overrides-$epoch.$major/scala"),
          file("scalalib/src/main/scala/scala"), overwrite = true)
      },

      compile in Compile <<= (compile in Compile) dependsOn assembleScalaLibrary
    ).
    dependsOn(javalib)

lazy val shouldPartest = settingKey[Boolean](
  "Whether we should partest the current scala version (and fail if we can't)")

lazy val partest = 
  project.in(file("partest")).
    settings(
      resolvers += Resolver.typesafeIvyRepo("releases"),
      libraryDependencies ++= {
        if (shouldPartest.value)
          Seq(
            "org.scala-sbt" % "sbt" % sbtVersion.value,
            "org.scala-lang.modules" %% "scala-partest" % "1.0.13"
          )
        else Seq()
      },
      sources in Compile := {
        if (shouldPartest.value) {
          // Partest sources and some sources of sbtplugin (see above)
          val baseSrcs = (sources in Compile).value

          baseSrcs
        } else Seq()
      }
  ).dependsOn(scalalib)

lazy val demoNative =
  project.in(file("demo/native")).
    settings(libSettings).
    settings(
      nativeVerbose := true,
      nativeClangOptions := Seq("-O2")
    ).
    dependsOn(scalalib)

lazy val demoJVM =
  project.in(file("demo/jvm")).
    settings(
      fork in run := true,
      javaOptions in run ++= Seq("-Xms64m", "-Xmx64m")
    )
