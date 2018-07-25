package com.pinterest.pinlater.commons.util;

import java.io.File;
import java.text.NumberFormat;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class SystemInfoUtil {
	
	private Runtime runtime = Runtime.getRuntime();

    public String Info() {
    		printUsage();
        StringBuilder sb = new StringBuilder();
        sb.append(this.OsInfo());
        sb.append(this.MemInfo());
        sb.append(this.DiskInfo());
        return sb.toString();
    		
    }

    public String OSname() {
        return System.getProperty("os.name");
    }

    public String OSversion() {
        return System.getProperty("os.version");
    }

    public String OsArch() {
        return System.getProperty("os.arch");
    }

    public long totalMem() {
        return Runtime.getRuntime().totalMemory();
    }

    public long usedMem() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public String MemInfo() {
        NumberFormat format = NumberFormat.getInstance();
        StringBuilder sb = new StringBuilder();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        sb.append("Free memory: ");
        sb.append(format.format(freeMemory / 1024));
        sb.append("<br/>");
        sb.append("Allocated memory: ");
        sb.append(format.format(allocatedMemory / 1024));
        sb.append("<br/>");
        sb.append("Max memory: ");
        sb.append(format.format(maxMemory / 1024));
        sb.append("<br/>");
        sb.append("Total free memory: ");
        sb.append(format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024));
        sb.append("<br/>");
        return sb.toString();

    }

    public String OsInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("OS: ");
        sb.append(this.OSname());
        sb.append("<br/>");
        sb.append("Version: ");
        sb.append(this.OSversion());
        sb.append("<br/>");
        sb.append(": ");
        sb.append(this.OsArch());
        sb.append("<br/>");
        sb.append("Available processors (cores): ");
        sb.append(runtime.availableProcessors());
        sb.append("<br/>");
        return sb.toString();
    }

    public String DiskInfo() {
        /* Get a list of all filesystem roots on this system */
        File[] roots = File.listRoots();
        StringBuilder sb = new StringBuilder();

        /* For each filesystem root, print some info */
        for (File root : roots) {
            sb.append("File system root: ");
            sb.append(root.getAbsolutePath());
            sb.append("<br/>");
            sb.append("Total space (bytes): ");
            sb.append(root.getTotalSpace());
            sb.append("<br/>");
            sb.append("Free space (bytes): ");
            sb.append(root.getFreeSpace());
            sb.append("<br/>");
            sb.append("Usable space (bytes): ");
            sb.append(root.getUsableSpace());
            sb.append("<br/>");
        }
        return sb.toString();
    }
    
    public static int getPID() {
    		RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
    		Field jvm = null;
			try {
				jvm = runtime.getClass().getDeclaredField("jvm");
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		jvm.setAccessible(true);
    		sun.management.VMManagement mgmt = null;
			try {
				mgmt = (sun.management.VMManagement) jvm.get(runtime);
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		Method pid_method = null;
			try {
				pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		pid_method.setAccessible(true);

    		int pid = 0;
			try {
				pid = (Integer) pid_method.invoke(mgmt);
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		return pid;
    }
    
    public static void printUsage() {
    	  OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    	  System.out.println("Process id and hostname: " + ManagementFactory.getRuntimeMXBean().getName());
    	  for (Method method : operatingSystemMXBean.getClass().getDeclaredMethods()) {
    	    method.setAccessible(true);
    	    if (method.getName().startsWith("get")
    	        && Modifier.isPublic(method.getModifiers())) {
    	            Object value;
    	        try {
    	            value = method.invoke(operatingSystemMXBean);
    	        } catch (Exception e) {
    	            value = e;
    	        } // try
    	        System.out.println(method.getName() + " = " + value);
    	    } // if
    	  } // for
    	}
}
