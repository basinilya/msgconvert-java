@set tag=%1
@if x==x%tag% ( 
  @>&2 echo tag name not provided on command line
  SET /P tag=enter the tag: 
) 

docker image tag msgconvert-java repohost/user/msgconvert-java:%tag%
docker image push repohost/user/msgconvert-java:%tag%
