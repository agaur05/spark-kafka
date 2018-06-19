enablePlugins(JavaAppPackaging)

name := "ParticulateMatter"

version := "0.1"

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.2.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-hive" % "2.2.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-streaming" % "2.2.1" % "provided"
libraryDependencies += "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.2.1"
libraryDependencies += "com.typesafe" % "config" % "1.3.1"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.apache.kafka" %% "kafka" % "0.10.0.1" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

// Define basic assembly strategy
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false)
test in assembly := {}

// Turn off parallel tests runs in Spark
parallelExecution in Test := false // cannot run Spark tests in paralell

// Setup universal packaging
mappings in Universal := {
  // universalMappings: Seq[(File,String)]
  val universalMappings = (mappings in Universal).value
  val fatJar = (assembly in Compile).value
  // removing means filtering
  val filtered = universalMappings filter {
    case (_, name) =>  ! name.endsWith(".jar") && ! name.endsWith(".bat")
  }
  // add the fat jar
  filtered :+ (fatJar -> ("lib/" + fatJar.getName))
}

// the bash scripts classpath only needs the fat jar
scriptClasspath := Seq( (assemblyJarName in assembly).value )
executableScriptName := "spark.sh"