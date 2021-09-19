kubectl delete -f ./persistentVolume-minio.yaml
kubectl apply -f ./persistentVolume-minio.yaml
kubectl delete -f ./persistentVolume-jupyter.yaml
kubectl apply -f ./persistentVolume-jupyter.yaml
kubectl delete -f ./persistentVolume-mongoxp.yaml
kubectl apply -f ./persistentVolume-mongoxp.yaml
