# Kubernetes + Istio + Into local environment

- [Kubernetes + Istio + Into local environment](#kubernetes--istio--into-local-environment)
  - [Install k3d](#install-k3d)
  - [Create cluster](#create-cluster)
  - [Deploy Application](#deploy-application)
  - [Istio](#istio)
    - [Download Istio](#download-istio)
    - [Create a namespace for istio](#create-a-namespace-for-istio)
    - [Install istio-init](#install-istio-init)
    - [Install istio](#install-istio)
    - [Test with istio demo application](#test-with-istio-demo-application)
    - [Enable istio-injection into demo-app](#enable-istio-injection-into-demo-app)
    - [Configuring ingress using an Istio gateway](#configuring-ingress-using-an-istio-gateway)
    - [Api versionning](#api-versionning)
      - [Version 1](#version-1)
      - [Version 1 and Version 2 at the same time](#version-1-and-version-2-at-the-same-time)
  - [References](#references)

## Install k3d 

```shell
curl -s https://raw.githubusercontent.com/rancher/k3d/v1.3.4/install.sh | bash
```
## Create cluster

create k3s cluster without traefik see [https://github.com/rancher/k3d/issues/104] for more details.

```shell
# Create the cluster
$ k3d create --server-arg --no-deploy --server-arg traefik
INFO[0000] Created cluster network with ID 0a2115861a4a32a21cb52ad96980e697f73275280761066e17aa1d1b0d8de261 
INFO[0000] Created docker volume  k3d-k3s-default-images 
INFO[0000] Creating cluster [k3s-default]               
INFO[0000] Creating server using docker.io/rancher/k3s:v0.10.0... 
INFO[0000] SUCCESS: created cluster [k3s-default]       
INFO[0000] You can now use the cluster with:

# generate kubectl configuration
$ export KUBECONFIG="$(k3d get-kubeconfig --name='k3s-default')"

# check the result
$ kubectl get pod,svc --all-namespaces
NAMESPACE     NAME                                          READY   STATUS    RESTARTS   AGE
kube-system   pod/local-path-provisioner-58fb86bdfd-n5tpt   1/1     Running   0          26s
kube-system   pod/coredns-57d8bbb86-7998g                   1/1     Running   0          26s

NAMESPACE     NAME                 TYPE        CLUSTER-IP   EXTERNAL-IP   PORT(S)                  AGE
default       service/kubernetes   ClusterIP   10.43.0.1    <none>        443/TCP                  43s
kube-system   service/kube-dns     ClusterIP   10.43.0.10   <none>        53/UDP,53/TCP,9153/TCP   42s
```

## Deploy Application

Create an new spring-boot application using https://start.spring.io/.
For this tuto we will use an already prepared application from `demo-app` folder.

```shell
# checkout the 1.0 tag
$ git checkout 1.0

# build application jar and docker image
$ JAVA_HOME=~/dev/tools/jdk-13.0.1-9-hotspot ./mvnw package

```
You can see that there are a docker image `com.example/demo-istio` 
```shell
$ docker image ls
REPOSITORY               TAG                    IMAGE ID            CREATED             SIZE
...
...
com.example/demo-istio   1.0                    3cf5e8e423b9        49 years ago        383MB
```

To deploy our application into the k3s cluster we have to make our local image available into the cluster.
This can be done by push the image to a remote docker registry or by using the import-images

```shell
$ k3d import-images com.example/demo-app:1.0
INFO[0000] Saving images [com.example/demo-app:1.0] from local docker daemon... 
INFO[0005] Saved images to shared docker volume         
INFO[0005] Importing images [com.example/demo-app:1.0] in container [k3d-k3s-default-server] 
INFO[0014] Successfully imported images [com.example/demo-app:1.0] in all nodes of cluster [k3s-default] 
INFO[0014] Cleaning up tarball                          
INFO[0015] Deleted tarball                              
INFO[0015] ...Done 
```

Into k8s folder you can the k8s manifest we will use to deploy.  
The demo-app will be deployed into namespace `api`
```shell
$ tree k8s/
k8s/
├── 01_namespace.yml
├── 02_deployment.yml
└── 03_service.yml
```

Fill free to check the content of YAML file after that
```shell
# deploy and wait to deployment to finish
$ kubectl apply -R -f k8s && kubectl -n api get pods -w
namespace/api created
deployment.apps/demo-app-deployment created
service/demo-app-service created
NAME                                  READY   STATUS              RESTARTS   AGE
demo-app-deployment-f444966fd-4qh5n   0/1     ContainerCreating   0          0s
demo-app-deployment-f444966fd-4qh5n   0/1   Running   0     2s
demo-app-deployment-f444966fd-4qh5n   1/1   Running   0     5s
# CTRL+C to stop waiting
# pod is running and service is there
$ kubectl -n api get pods,svc 
NAME                                      READY   STATUS    RESTARTS   AGE
pod/demo-app-deployment-f444966fd-4qh5n   1/1     Running   0          94s

NAME                       TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)   AGE
service/demo-app-service   ClusterIP   10.43.113.227   <none>        80/TCP    94s

```

To check that application is running, we connect to the server docker container and run a simple http request. 

```shell
$ docker exec -ti k3d-k3s-default-server sh -c "wget -qSO- 10.43.113.227/actuator/info"
  HTTP/1.1 200 
  Content-Type: application/vnd.spring-boot.actuator.v3+json
  Transfer-Encoding: chunked
  Date: Thu, 05 Dec 2019 12:33:03 GMT
  Connection: close
  
{"git":{"branch":"67f84b9744d014f558b95fb521266fa4fb00501a","commit":{"id":"67f84b9","time":"2019-12-05T12:04:13Z"}}}
```

## Istio

### Download Istio
```shell
$ curl https://github.com/istio/istio/releases/download/1.4.0/istio-1.4.0-linux.tar.gz -L | tar xvz
```

### Create a namespace for istio
```shell
$ cd istio-1.4.0
$ kubectl create namespace istio-system
namespace/istio-system created
```
### Install istio-init
```shell
$ helm template istio-init install/kubernetes/helm/istio-init  --namespace istio-system | kubectl apply -f -
configmap/istio-crd-10 created
configmap/istio-crd-11 created
configmap/istio-crd-14 created
serviceaccount/istio-init-service-account created
clusterrole.rbac.authorization.k8s.io/istio-init-istio-system created
clusterrolebinding.rbac.authorization.k8s.io/istio-init-admin-role-binding-istio-system created
job.batch/istio-init-crd-10-1.4.0 created
job.batch/istio-init-crd-11-1.4.0 created
job.batch/istio-init-crd-14-1.4.0 created

# Wait that `istio-init-crd` are `Completed` 
$ kubectl get pod,svc -n istio-system 
NAME                                READY   STATUS      RESTARTS   AGE
pod/istio-init-crd-11-1.4.0-8x5nw   0/1     Completed   0          80s
pod/istio-init-crd-14-1.4.0-hr46l   0/1     Completed   0          80s
pod/istio-init-crd-10-1.4.0-fvnwp   0/1     Completed   0          80s
```

### Install istio

```shell
$ cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: kiali
  namespace: istio-system
  labels:
    app: kiali
type: Opaque
data:
  username: YWRtaW4=
  passphrase: YWRtaW4=
EOF
```

```shell
$ helm template istio install/kubernetes/helm/istio --namespace istio-system --set kiali.enabled=true | kubectl apply -f -
poddisruptionbudget.policy/istio-galley created
poddisruptionbudget.policy/istio-ingressgateway created
poddisruptionbudget.policy/istio-telemetry created
...
...
instance.config.istio.io/tcpconnectionsopened created
instance.config.istio.io/tcpbytereceived created
rule.config.istio.io/tcpkubeattrgenrulerule created
rule.config.istio.io/kubeattrgenrulerule created
rule.config.istio.io/promtcp created
rule.config.istio.io/promtcpconnectionopen created
rule.config.istio.io/promhttp created
rule.config.istio.io/promtcpconnectionclosed created
job.batch/istio-security-post-install-1.4.0 created

# check that every thing is OK (status should be Completed or Running) 
$ kubectl get pod,svc -n istio-system 
NAME                                          READY   STATUS      RESTARTS   AGE
pod/istio-init-crd-11-1.4.0-6njlk             0/1     Completed   0          3m47s
pod/istio-init-crd-14-1.4.0-bj95w             0/1     Completed   0          3m47s
pod/istio-init-crd-10-1.4.0-brttn             0/1     Completed   0          3m47s
pod/svclb-istio-ingressgateway-tvqtx          9/9     Running     0          114s
pod/istio-security-post-install-1.4.0-p29g6   0/1     Completed   0          112s
pod/istio-citadel-684bbc76db-nmtmq            1/1     Running     0          113s
pod/istio-policy-6bff58b654-b6wm7             2/2     Running     2          114s
pod/istio-telemetry-5f5fc47f99-87snk          2/2     Running     2          113s
pod/istio-galley-7868d4ddb4-tdzt7             1/1     Running     0          114s
pod/istio-sidecar-injector-cf6f95c6f-ssq8d    1/1     Running     0          113s
pod/prometheus-794594dc97-9fj57               1/1     Running     0          113s
pod/istio-pilot-fc7fb79bb-zxtgm               2/2     Running     2          113s
pod/istio-ingressgateway-7c8585587f-jvxh4     1/1     Running     0          114s

NAME                             TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                                                                                                                      AGE
service/istio-galley             ClusterIP      10.43.0.111     <none>        443/TCP,15014/TCP,9901/TCP                                                                                                                   114s
service/istio-telemetry          ClusterIP      10.43.65.162    <none>        9091/TCP,15004/TCP,15014/TCP,42422/TCP                                                                                                       114s
service/istio-policy             ClusterIP      10.43.254.76    <none>        9091/TCP,15004/TCP,15014/TCP                                                                                                                 114s
service/istio-pilot              ClusterIP      10.43.8.87      <none>        15010/TCP,15011/TCP,8080/TCP,15014/TCP                                                                                                       114s
service/prometheus               ClusterIP      10.43.44.76     <none>        9090/TCP                                                                                                                                     114s
service/istio-citadel            ClusterIP      10.43.123.246   <none>        8060/TCP,15014/TCP                                                                                                                           114s
service/istio-sidecar-injector   ClusterIP      10.43.21.22     <none>        443/TCP,15014/TCP                                                                                                                            114s
service/istio-ingressgateway     LoadBalancer   10.43.56.171    172.22.0.2    15020:30669/TCP,80:31380/TCP,443:31390/TCP,31400:31400/TCP,15029:30109/TCP,15030:32699/TCP,15031:31643/TCP,15032:31272/TCP,15443:30126/TCP   114s

```
### Test with istio demo application

```shell
$ kubectl label namespace default istio-injection=enabled
$ kubectl apply -f samples/bookinfo/platform/kube/bookinfo.yaml
$ kubectl get pods -w
$ kubectl apply -f samples/bookinfo/networking/bookinfo-gateway.yaml
$ kubectl get svc  -n istio-system istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

Goto this url to check istio demo working [http://{The IP Address}/productpage]()

### Enable `istio-injection` into `demo-app`

Into this version `1.0-istio-injectio-on` we enable the `istio-injection` by added a label into namespace `api`
```shell
$ git checkout 1.0-istio-injectio-on

$ git diff 1.0..1.0-istio-injectio-on 
diff --git a/k8s/01_namespace.yml b/k8s/01_namespace.yml
index b61d02d..6bd1bae 100644
--- a/k8s/01_namespace.yml
+++ b/k8s/01_namespace.yml
@@ -2,7 +2,7 @@
 apiVersion: v1
 kind: Namespace
 metadata:
-  # labels:
-  #   # enable istio injection into the new namespace
-  #   istio-injection: enabled
-  name: api
\ No newline at end of file
+  labels:
+    # enable istio injection into the new namespace
+    istio-injection: enabled
+  name: api
```

When we apply this modification, we can see that only namespace change
pod is not updated. 

```shell
$ kubectl apply -R -f k8s && kubectl -n api get pods
namespace/api configured
deployment.apps/demo-app-deployment unchanged
service/demo-app-service unchanged
NAME                                  READY   STATUS    RESTARTS   AGE
demo-app-deployment-f444966fd-4qh5n   1/1     Running   0          61m
```

We need to restart pods to make the change applied. There are no restart command provided by kubectl. To achieve that we patch the deployment by adding a label this will make k8s create a new revision with the same image version  

```shell
$ kubectl patch -n api deployment demo-app-deployment -p   "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"restart_date\":\"$(date +'%s')\"}}}}}"
deployment.apps/demo-app-deployment patched

$ kubectl -n api get pods 
NAME                                   READY   STATUS    RESTARTS   AGE
demo-app-deployment-577f556689-tlpnn   2/2     Running   0          18s
```

You can see that the pod name has changed and under `READY` we have `2/2` instead of `1/1`.
We can see that there are a new container called `istio-proxy`

```shell
$ kubectl -n api get pods demo-app-deployment-577f556689-tlpnn -o json | jq -r .spec.containers[].name
demo-app
istio-proxy
```
### Configuring ingress using an Istio gateway

To expose the `demo-app` outside the k8s cluster we need an `Istio Gateway` and an `Istio VirtualService`. An ingress Gateway describes a load balancer operating at the edge of the mesh that receives incoming HTTP/TCP connections. It configures exposed ports, protocols, etc. but, unlike Kubernetes Ingress Resources, does not include any traffic routing configuration. Traffic routing for ingress traffic is instead configured using Istio routing rules, exactly in the same way as for internal service requests.

```shell
$ git checkout 1.0-istio-gateway-as-ingress
```

This version add a new file k8s/04_gateway.yml, that contains the definition of the `demo-app-gateway` and the `demo-app-route`. We only expose the `/actuator/*` endpoints.

```shell
# Apply changes
$ kubectl apply -R -f k8s 
namespace/api unchanged
deployment.apps/demo-app-deployment unchanged
service/demo-app-service unchanged
gateway.networking.istio.io/demo-app-gateway created
virtualservice.networking.istio.io/demo-app-route created

# check that gateway is there
$ kubectl get gateway -n api
NAME               AGE
demo-app-gateway   77s

# check that virtualservice is there
$ kubectl get virtualservices.networking.istio.io -n api
NAME             GATEWAYS             HOSTS   AGE
demo-app-route   [demo-app-gateway]   [*]     89s

# get the external IP of the istio-ingressgateway
$ kubectl get svc  -n istio-system istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
172.24.0.2

# check that `demo-app` is accessible from outsie the k8s cluster
$ curl -v -s 172.24.0.2/actuator/info
*   Trying 172.24.0.2:80...
* TCP_NODELAY set
* Connected to 172.24.0.2 (172.24.0.2) port 80 (#0)
> GET /actuator/info HTTP/1.1
> Host: 172.24.0.2
> User-Agent: curl/7.65.3
> Accept: */*
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< content-type: application/vnd.spring-boot.actuator.v3+json
< date: Thu, 05 Dec 2019 16:02:39 GMT
< x-envoy-upstream-service-time: 3
< server: istio-envoy
< transfer-encoding: chunked
< 
* Connection #0 to host 172.24.0.2 left intact
{"git":{"branch":"67f84b9744d014f558b95fb521266fa4fb00501a","commit":{"id":"67f84b9","time":"2019-12-05T12:04:13Z"}}}
```

### Api versionning

#### Version 1

```shell
git checkout 1.1
```

This version contains a new Rest endpoint that generate a random name. 

```shell
# build the version 1.1 of the docker image
$ JAVA_HOME=~/dev/tools/jdk-13.0.1-9-hotspot ./mvnw package

$ $ docker image ls
REPOSITORY               TAG                    IMAGE ID            CREATED             SIZE
...
com.example/demo-app     1.1                    cba87da91be8        49 years ago        385MB
```

> the deployment.yml reference now the 1.1 version of the image.
> We need to import this new version into k3s cluster.
> We have to update the `virtualservice` to be able to reach the new '/v1/hello' endpoint

```shell
$ k3d import-images com.example/demo-app:1.1

# deploy modification
$ kubectl apply -R -f k8s
namespace/api unchanged
deployment.apps/demo-app-deployment configured
service/demo-app-service unchanged
gateway.networking.istio.io/demo-app-gateway unchanged
virtualservice.networking.istio.io/demo-app-route configured

$ curl -v -s 172.24.0.2/v1/hello
*   Trying 172.24.0.2:80...
* TCP_NODELAY set
* Connected to 172.24.0.2 (172.24.0.2) port 80 (#0)
> GET /v1/hello HTTP/1.1
> Host: 172.24.0.2
> User-Agent: curl/7.65.3
> Accept: */*
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< content-type: text/plain;charset=UTF-8
< content-length: 18
< date: Thu, 05 Dec 2019 17:52:10 GMT
< x-envoy-upstream-service-time: 5
< server: istio-envoy
< 
* Connection #0 to host 172.24.0.2 left intact
Hello, Mel Maggio!
```

#### Version 1 and Version 2 at the same time

When migrating from version 1 to version 2 of an API, We need to give time to client of this API to migrate, So we need to have the two version of API running together.
To do that we will have to deployment, one for each version.

> Note the `version` label: this is very important for Istio to distinguish between the two deployments


```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-app-deployment-v1
spec:
...
  template:
    metadata:
      labels:
        app: demo-app
        version: v1.1
    spec:
      containers:
      - name: demo-app
        image: com.example/demo-app:1.1
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: demo-app-deployment-v2
spec:
...
  template:
    metadata:
      labels:
        app: demo-app
        version: v1.2
    spec:
      containers:
      - name: demo-app
        image: com.example/demo-app:1.2
```

No big change to do into the kubernetes `service`
> Note that the port is named (“name: http”). This is a requirement for Istio.

> The selector is only using the `demo-app` label. Without Istio it will distribute traffic between the two deployments evenly.

```yaml
apiVersion: v1
kind: Service
metadata:
  namespace: api
  name: demo-app-service
  labels:
    app: demo-app
spec:
  ports:
    - port: 80
      name: http
      targetPort: 8080
  type: ClusterIP
  selector:
    app: demo-app
```

For `VirtualService` side, we use the subset. A subset/version of a route destination is identified with a reference to a named service subset which must be declared in a corresponding DestinationRule.

```yaml
  http:
  - match:
    - uri:
        prefix: /v1
    route:
    - destination:
        host: demo-app-service
        subset: v1
        port:
          number: 80
  - match:
    - uri:
        prefix: /v2
    route:
    - destination:
        host: demo-app-service
        subset: v2
        port:
          number: 80
```

And here the definition of DestinationRule that make the mapping between `Sebset/version` and `pods/version`


```yaml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: demo-app-rules
  namespace: api
spec:
  host: demo-app-service
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2

```




## References

- [Tried k8s + Istio on my laptop with k3d](https://dev.to/bufferings/tried-k8s-istio-in-my-local-machine-with-k3d-52gg)
- [Managing Microservices Traffic with Istio](https://haralduebele.blog/2019/03/11/managing-microservices-traffic-with-istio/)
- [Istio Docs](https://istio.io/docs/)