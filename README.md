# connect-server

This project defines Google App Engine services to be paired with [connect-app](https://github.com/yanivian/connect-app).

To deploy the frontend (default) service, run:
```
./gradlew :frontend:appengineDeploy
```

To deploy the async service, run:
```
./gradlew :async:appengineDeploy
```

To deploy the Cloud Datastore indexes, run:
```
./gradlew :backend:appengineDeployIndex
```

To deploy Cloud Task queues, run:
```
./gradlew :backend:appengineDeployQueue
```
