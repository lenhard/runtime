package at.tuwien.infosys.entities;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;



@Entity
public class DockerContainer {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private long id;
    private String containerid;
    private String host;
    private String image;
    private String operator;
    private String status;
    private String terminationTime;
    private Double cpuCores;
    private Integer ram;
    private Integer storage;
    
    private String monitoringPort;
    
    /* Monitoring Information */
    private double cpuUsagePercentage;
    private long previousCpuUsage;
    private long previousSystemUsage;
    
    public String getContainerid() {
        return containerid;
    }

    public void setContainerid(String containerid) {
        this.containerid = containerid;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTerminationTime() {
        return terminationTime;
    }

    public void setTerminationTime(String terminationTime) {
        this.terminationTime = terminationTime;
    }

    public Double getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Double cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Integer getRam() {
        return ram;
    }

    public void setRam(Integer ram) {
        this.ram = ram;
    }

    public Integer getStorage() {
        return storage;
    }

    public void setStorage(Integer storage) {
        this.storage = storage;
    }

    public DockerContainer() {
        this.previousCpuUsage = 0;
        this.previousSystemUsage = 0;
        this.cpuUsagePercentage = 0.0;
        this.monitoringPort = "";
    }

    public double getCpuUsagePercentage() {
		return cpuUsagePercentage;
	}

	public void setCpuUsagePercentage(double cpuUsagePercentage) {
		this.cpuUsagePercentage = cpuUsagePercentage;
	}

	public long getPreviousCpuUsage() {
		return previousCpuUsage;
	}

	public void setPreviousCpuUsage(long previousCpuUsage) {
		this.previousCpuUsage = previousCpuUsage;
	}

	public long getPreviousSystemUsage() {
		return previousSystemUsage;
	}

	public void setPreviousSystemUsage(long previousSystemUsage) {
		this.previousSystemUsage = previousSystemUsage;
	}

	public String getMonitoringPort() {
		return monitoringPort;
	}

	public void setMonitoringPort(String monitoringPort) {
		this.monitoringPort = monitoringPort;
	}

	public DockerContainer(String operator, Double cpuCores, Integer ram, Integer storage) {
        this.operator = operator;
        this.cpuCores = cpuCores;
        this.ram = ram;
        this.storage = storage;
        this.status = "running";
        this.previousCpuUsage = 0;
        this.previousSystemUsage = 0;
        this.cpuUsagePercentage = 0.0;
        this.monitoringPort = "";
    }


    @Override
    public String toString() {
        return "DockerContainer{" +
                "id=" + id +
                ", containerid='" + containerid + '\'' +
                ", host='" + host + '\'' +
                ", image='" + image + '\'' +
                ", operator='" + operator + '\'' +
                ", status='" + status + '\'' +
                ", terminationTime='" + terminationTime + '\'' +
                ", cpuCores=" + cpuCores +
                ", ram=" + ram +
                ", storage=" + storage +
                '}';
    }
}
