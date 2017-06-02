package com.gfk.mri.constants;

import java.text.NumberFormat;
import java.text.ParseException;

public class Test {
	
	public static void main(String[] args)
	{
		String amt = "2,500.04 Cr";
		
		 try
		{
			 
			Number number = NumberFormat.getNumberInstance(java.util.Locale.US).parse(amt);
			
			System.out.println(number);
			
			
			
		} catch (ParseException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
