# kubectl and helm should be installed
helm repo add stable https://charts.helm.sh/stable
helm repo add argo https://argoproj.github.io/argo-helm
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add minio https://helm.min.io/

#helm install nginx-ingress stable/nginx-ingress --set controller.stats.enabled=true --set controller.kind=DaemonSet --set controller.daemonset.useHostPort=true  --set controller.service.type=NodePort --namespace kube-system --set defaultBackend.nodeSelector.tier=manager

cd chart
helm dep update
cd ..
kubectl apply -f ./cluster-role-binding-manager.yaml
kubectl apply -f ./cluster-role-binding-default.yaml
kubectl apply -f ./secret-minio.yaml