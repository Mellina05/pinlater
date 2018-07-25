package com.pinterest.pinlater.commons.util;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class SystemInfoUtilTest {
	private SystemInfoUtil systemInfo;
	
	@Before
	public void setup() {
		systemInfo = new SystemInfoUtil();
	}
	
	@Test
	public void testInfo() {
		System.out.println(systemInfo.Info());
	}
	
	@Test
	public void testGetPID() {
		System.out.println(SystemInfoUtil.getPID());
	}
	
	@Test
	public void testOSName() {
		assertEquals(System.getProperty("os.name"), systemInfo.OSname());
	}

}
