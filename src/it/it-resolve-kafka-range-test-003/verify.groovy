def buildLog = new File( basedir, "build.log")

assert buildLog.text.contains( '[INFO] Skip version range resolve since property is not a valid range' )

return true