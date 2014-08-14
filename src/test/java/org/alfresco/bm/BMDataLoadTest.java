/*
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.bm;

import java.io.IOException;
import java.util.Properties;

import org.alfresco.bm.tools.BMTestRunner;

/**
 * Initiates the application context associated with this test.
 * 
 * @author Derek Hulley
 * @since 1.0
 */
public class BMDataLoadTest
{
    public static void main(String ... args) throws IOException
    {
        // Test properties
        Properties testProperties = new Properties();
        testProperties.load(BMDataLoadTest.class.getResourceAsStream("/default.properties"));
        
        String testNameOriginal = "bm-dataload";
        String testName = testNameOriginal.replace("-", "_");
        
        BMTestRunner testRunner = new BMTestRunner(
                testNameOriginal + "-context.xml",
                2,
                "mongodb://127.0.0.1:27017/test",
                testName, "run_" + System.currentTimeMillis(),
                testProperties);
        
        testRunner.start();
        System.out.println("Hit any key to end run.");
        try
        {
            System.in.read();
        }
        catch (Throwable e) {}
        
        testRunner.stop();
    }
}
