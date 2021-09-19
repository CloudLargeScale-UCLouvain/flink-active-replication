# local-path must be installed, and directories created on each node
#./label-nodes.sh
# take care of all directories creation before pv/pvc creation
kind delete cluster -q
kind create cluster --config kind-flink.yaml
kubectl create ns manager
kubectl config set-context --current --namespace=manager
kubectl apply -f pv
kubectl apply -f ./storage-class.yaml
cd ../../ManagerKubernetesExperimentations
./init.sh
cd chart
helm install manager . -f ../../cluster/kind/manager.yaml --namespace manager
