// main.bicep

@description('The location for all resources.')
param location string = resourceGroup().location

@description('A common prefix for all resource names.')
param resourceNamePrefix string = 'ledger'

// Azure Container Registry (ACR)
resource acr 'Microsoft.ContainerRegistry/registries@2021-09-01' = {
  name: '${resourceNamePrefix}acr${uniqueString(resourceGroup().id)}'
  location: location
  sku: {
    name: 'Standard'
  }
  properties: {
    adminUserEnabled: true
  }
}

// Azure Kubernetes Service (AKS)
resource aksCluster 'Microsoft.ContainerService/managedClusters@2022-03-01' = {
  name: '${resourceNamePrefix}-aks'
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    dnsPrefix: '${resourceNamePrefix}-dns'
    agentPoolProfiles: [
      {
        name: 'agentpool'
        count: 1
        vmSize: 'Standard_DS2_v2'
        mode: 'System'
        osType: 'Linux'
      }
    ]
  }
}

// Output the ACR login server name, which we'll need later
output acrLoginServer string = acr.properties.loginServer
