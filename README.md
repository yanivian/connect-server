# connect-server

This project defines Google App Engine services to be paired with [connect-app](https://github.com/yanivian/connect-app).

To deploy the frontend (default) service, run:
```
./gradlew :frontend:appengineDeploy
```

To deploy the Datastore index, run:
```
./gradlew :frontend:appengineDeployIndex
```
