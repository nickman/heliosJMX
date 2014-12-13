/**
 * Helios Development Group LLC, 2013
 */
package com.heliosapm.jmx.util.helpers;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>Title: ArrayUtils</p>
 * <p>Description: Some generic array utilities</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead 
 * <p><code>org.helios.rindle.util.ArrayUtils</code></p>
 */

public class ArrayUtils {

	private static final long[][] EMPTY_LONG_ARR = new long[0][0];
	
	private static long[][] validateAndInit(long[][] source) {
		if(source==null) throw new IllegalArgumentException("Null array passed");
		int size = source.length;
		if(size==0) return EMPTY_LONG_ARR;
		int secondary = source[0].length;
		for(int i = 1; i < source.length; i++) {
			if(source[i].length != secondary) throw new IllegalArgumentException("Pivot not supported on uneven primitive arrays");
		}		
		return new long[secondary][source.length];
	}
	
	/**
	 * Pivots an array. e.g. an array like this:<pre>
		[33, 2, 3, 4, 5]
		[10, 9, 8, 7, 6]
	 * </pre> turns into an array like this:<pre>
		[33, 10]
		[2, 9]
		[3, 8]
		[4, 7]
		[5, 6]
	 * </pre>
	 * @param source The array to pivot
	 * @return The pivoted array
	 */
	public static long[][] pivot(long[][] source) {
		long[][] target = validateAndInit(source);
		for(int x = 0; x < target.length; x++) {			
			for(int y = 0; y < source.length; y++) {
				target[x][y] = source[y][x];
			}
		}
		return target;
	}
	
	/**
	 * Trims all the strings in the passed array, 
	 * removing any entries that are null or empty
	 * @param strings The array to trim
	 * @return the trimmed array
	 */
	public static String[] trim(String...strings) {
		if(strings==null) return new String[0];
		List<String> result = new ArrayList<String>();
		for(int i = 0; i < strings.length; i++) {
			if(strings[i]==null) continue;			
			strings[i] = strings[i].trim();
			if(!strings[i].isEmpty()) result.add(strings[i]);
		}
		return result.toArray(new String[0]);
	}
	
	
	/**
	 * Prints a formatted matrix from the passed array
	 * @param arr The array to print
	 * @return a matrix string
	 */
	public static String formatArray(long[][] arr) {
		if(arr==null) return "";
		if(arr.length==0) return "";
		StringBuilder b = new StringBuilder();
		for(int x = 0; x < arr.length; x++) {
			b.append(Arrays.toString(arr[x])).append("\n");
		}
		return b.toString();
		
	}
	
	/**
	 * Returns the dimension of the passed object array.
	 * If the object is not an array, will throw an {@link IllegalArgumentException}
	 * @param arr The array object
	 * @return the dimension of the array
	 */
	public static int getArrayDimension(final Object arr) {
		if(arr==null) throw new IllegalArgumentException("The passed object was null");
		if(!arr.getClass().isArray()) throw new IllegalArgumentException("The passed object is not an array");		
		return arr.getClass().getName().lastIndexOf("[")+1; 		
	}

	/**
	 * Returns the base type of an array object.
	 * Uses {@link Class#isArray()} but invokes recursively to get to the root type
	 * If the object is not an array, will throw an {@link IllegalArgumentException}.
	 * in the case of multi dimensional array types. 
	 * @param arr The array object to the root type of
	 * @return the root type of the array
	 */
	public static Class<?> getArrayBaseType(final Object arr) {
		final int dim = getArrayDimension(arr);
		Class<?> nextType = arr.getClass();
		for(int i = 0; i < dim; i++) {
			nextType = nextType.getComponentType();
		}
		return nextType;
	}
	
	/**
	 * Returns the base type of an array type
	 * @param type The array type
	 * @return The base type
	 */
	public static Class<?> getArrayTypeBaseType(final Class<?> type) {
		if(!type.isArray()) return type;
		Class<?> nextType = type.getComponentType();
		while(nextType.isArray()) {
			nextType = nextType.getComponentType();
		}
		return nextType;
	}
	
	/**
	 * Flattens a [possibly multi-dimensional] array into a one dimensional array.
	 * If the object is not an array, will throw an {@link IllegalArgumentException}.
	 * @param arr The array object to flatten
	 * @return the flattened array
	 */
	public static <T> T[] flatten(final Object arr) {		
		if(arr==null) throw new IllegalArgumentException("The passed object was null");
		if(!arr.getClass().isArray()) {
			T[] tarr = (T[])Array.newInstance(arr.getClass(), 1);
			tarr[0] = (T)arr;
			return tarr;
		}
		Object currentArr = arr;
		Class<?> currentType = currentArr.getClass();
		while(currentType.isArray()) {
			currentArr = reduce(currentArr);
			currentType = currentArr.getClass().getComponentType();
		}
		return (T[])currentArr;		
	}
	
	/**
	 * Determines if the passed object is or can be converted to an {@link Iterable}
	 * @param obj the object to test
	 * @return true if the passed object is or can be converted to an {@link Iterable}, false otherwise
	 */
	public static boolean isIterable(final Object obj) {
		if(obj==null) return false;
		if(obj instanceof Iterable) return true;
		if(obj.getClass().isArray()) return true;
		if(obj instanceof Map) return true;
		return false;
	}
	
	/**
	 * Converts or casts the passed object to an {@link Iterable}
	 * @param obj The object to cast or convert
	 * @return an {@link Iterable}
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> toIterable(final Object obj) {
		if(obj==null) throw new IllegalArgumentException("The passed object was null");
		final Class<?> clazz = obj.getClass();
		if(obj instanceof Iterable) {
			return (Iterable<T>)obj;
		} else if(clazz.isArray()) {
			T[] arr = flatten(obj);
			Collection<T> iter = new ArrayList<T>(arr.length);
			Collections.addAll(iter, arr);
			return iter;
		} else if(obj instanceof Map) {
			Map<Object, Object> map = (Map<Object, Object>)obj;
			return (Iterable<T>)map.entrySet();
		} else {
			throw new IllegalArgumentException("Don't know how to iterate type [" + clazz.getName() + "]");
		}
	}
	
	public static <T> Iterable<T>[] toIterables(final Object...objs) {
		if(objs==null || objs.length==0) throw new IllegalArgumentException("The passed object array was null or empty");
		Iterable[] iters = new Iterable[objs.length];
		for(int i = 0; i < objs.length; i++) {
			iters[i] = toIterable(objs[i]);
		}
		return iters;
	}

	
	
	public static String typeName(final Object arr) {
		if(arr==null) throw new IllegalArgumentException("The passed object was null");
		if(!arr.getClass().isArray()) return arr.getClass().getName();
		final StringBuilder b = new StringBuilder(getArrayBaseType(arr).getName());
		final int len = getArrayDimension(arr);
		for(int i = 0; i < len; i++) {
			b.append("[]");
		}
		return b.toString();
	}
	
	static void log(Object fmt, Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	public static <T> T[] reduce(final Object arr) {
		if(arr==null) throw new IllegalArgumentException("The passed object was null");
		if(!arr.getClass().isArray()) throw new IllegalArgumentException("The passed object was not an array");
		final int dim = getArrayDimension(arr);
		if(dim<2) return (T[])arr;
		final int len = Array.getLength(arr);
		final Class<?> baseType = arr.getClass().getComponentType();
		if(len==0) return (T[])Array.newInstance(baseType, 0);
		int xlen = 0;
		int[] xlens = new int[len];
		for(int i = 0; i < len; i++) {
			xlens[i] = Array.getLength(Array.get(arr, i));			
			xlen += xlens[i];
		}
		Object tarr = Array.newInstance(baseType.getComponentType(), xlen);
		int xindex = 0;
		for(int i = 0; i < len; i++) {
			int thisLen = xlens[i];
			Object carr = Array.get(arr, i);
			for(int x = 0; x < thisLen; x++) {
				Array.set(tarr, xindex, Array.get(carr, x));
				xindex++;
			}
		}
		return (T[])tarr;
	}
	
	
//	public static <T> T[] flattenarr(T[]...arrs) {
//		if(arrs==null) return (T[])new Object[0];
//		int length = 0;
//		T t = null;
//		for(T[] tarr: arrs) {
//			length += tarr.length;
//			if(t==null && length>0) {
//				for(int i = 0; i < tarr.length; i++) {
//					if(tarr[i]!=null) {
//						t = tarr[i];
//						break;
//					}
//				}
//			}
//		}
//		if(length==0 || t==null) return (T[])new Object[0];
//		T[] result = (T[])Array.newInstance(t.getClass(), length);
//		int i = 0;
//		for(T[] tarr: arrs) {
//			for(T tx: tarr) {
//				result[i] = tx;
//				i++;
//			}
//		}
//		return result;
//	}
	
	public static void main(String[] args) {
		System.out.println("Arr Base Type Test");
		Object a = new String[0][0][0];
		System.out.println("Type:" + a.getClass().getName());
		System.out.println("isArr:" + a.getClass().isArray());
		System.out.println(getArrayBaseType(a).getName());
		System.out.println("===================");
		String[][][] sarr = new String[][][] {{{"Hello", "World"}, {"Jupiter", "Venus"}}, {{"Sausage", "Peppers"}, {"Linux", "Windows"}}};
		log("Starting Arr: %s", Arrays.deepToString(sarr));
		String[] s = flatten(sarr);
		System.out.println(Arrays.toString(s));
		System.out.println("===================");
		s = flatten(new String[]{"Superman", "Batman"});
		System.out.println(Arrays.toString(s));
	}
}
