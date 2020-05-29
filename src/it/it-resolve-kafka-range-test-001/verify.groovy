def buildLog = new File( basedir, "build.log")

assert !buildLog.text.contains( '[INFO] Setting ce.kafka.version property' )
assert !buildLog.text.contains( '[INFO] Setting kafka.version property' )

return true