package ac.at.tuwien.infosys.visp.runtime.entities;

import java.util.Comparator;

public class ResourceComparator {
    public static final Comparator<ResourceAvailability> AMOUNTOFCONTAINERASC = (o1, o2) -> o1.getAmountOfContainer().compareTo(o2.getAmountOfContainer());

    public static final Comparator<ResourceAvailability> FREECPUCORESASC = (o1, o2) -> o1.getCpuCores().compareTo(o2.getCpuCores());

    public static final Comparator<ResourceAvailability> FREERAMASC = (o1, o2) -> o1.getMemory().compareTo(o2.getMemory());

    public static final Comparator<ResourceAvailability> FREESTORAGEASC = (o1, o2) -> o1.getStorage().compareTo(o2.getStorage());

    public static final Comparator<ResourceAvailability> AMOUNTOFCONTAINERDESC = (o1, o2) -> o2.getAmountOfContainer().compareTo(o1.getAmountOfContainer());

    public static final Comparator<ResourceAvailability> FREECPUCORESDESC = (o1, o2) -> o2.getCpuCores().compareTo(o1.getCpuCores());

    public static final Comparator<ResourceAvailability> FREERAMDESC = (o1, o2) -> o2.getMemory().compareTo(o1.getMemory());

    public static final Comparator<ResourceAvailability> FREESTORAGEDESC = (o1, o2) -> o2.getStorage().compareTo(o1.getStorage());

}
