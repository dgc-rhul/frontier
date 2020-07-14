## Object Detection in Frontier

Build the project:
```
. ./build_object_detector.sh
```

In one terminal run the master:
```
./master
```

In another terminal run the worker by specifying a unique port number, e.g.:
```
./worker 3501
```

This program requires three workers, make sure that each worker is started in a separate terminal.