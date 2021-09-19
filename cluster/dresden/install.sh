# install nginx-ingress
#helm install nginx-ingress stable/nginx-ingress --set controller.stats.enabled=true --set controller.kind=DaemonSet --set controller.daemonset.useHostPort=true  --set controller.service.type=NodePort --namespace kube-system --set defaultBackend.nodeSelector.tier=manager

# local-path must be installed
# git clone https://github.com/rancher/local-path-provisioner.git
# cd local-path-provisioner
# kubectl create ns local-path-storage
# helm install local-path-storage --namespace local-path-storage ./deploy/chart/
# directories should be created on each node

./label-nodes.sh
# take care of all directories creation before pv/pvc creation
kubectl create ns manager
kubectl config set-context --current --namespace=manager
kubectl apply -f pv
cd ../../ManagerKubernetesExperimentations
./init.sh
cd chart

helm install manager . -f ../../cluster/dresden/manager.yaml 
