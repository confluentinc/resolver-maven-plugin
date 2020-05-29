def buildLog = new File( basedir, "build.log")

assert buildLog.text.contains( '[INFO] Setting ce.kafka.version property ce.kafka.version=6.0.1-1000-ce' )
assert buildLog.text.contains( '[INFO] Setting kafka.version property kafka.version=6.0.1-15-ccs' )

return true