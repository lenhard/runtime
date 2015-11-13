package at.tuwien.infosys.entities;


import java.util.List;

public class Operator {
    private String name;
    private List<Operator> sources;

    public Operator(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Operator> getSources() {
        return sources;
    }

    public void setSources(List<Operator> sources) {
        this.sources = sources;
    }

}
