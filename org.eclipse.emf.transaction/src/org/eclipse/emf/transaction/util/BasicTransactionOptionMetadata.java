/**
 * <copyright>
 * 
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 * 
 * </copyright>
 *
 * $Id: BasicTransactionOptionMetadata.java,v 1.1 2008/11/30 16:38:08 cdamus Exp $
 */

package org.eclipse.emf.transaction.util;

import java.util.Map;

import org.eclipse.emf.transaction.Transaction;

/**
 * A simple implementation of the {@link Transaction.OptionMetadata} interface.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class BasicTransactionOptionMetadata
		implements Transaction.OptionMetadata {

	private final Object option;

	private boolean isTag;

	private boolean isHereditary;

	private Class<?> type;

	private Object defaultValue;

	/**
	 * Initializes me with my option key.
	 * 
	 * @param option
	 *            my option key
	 */
	public BasicTransactionOptionMetadata(Object option) {
		this(option, true, true, Object.class, null);
	}

	/**
	 * Initializes me with my option key and other details.
	 * 
	 * @param option
	 *            my option key
	 * @param isTag
	 *            whether the option is a tag
	 * @param isHereditary
	 *            whether the option is inherited
	 * @param type
	 *            the option type
	 * @param defaultValue
	 *            the option's defaul value
	 */
	public BasicTransactionOptionMetadata(Object option, boolean isTag,
			boolean isHereditary, Class<?> type, Object defaultValue) {
		this.option = option;
		this.isTag = isTag;
		this.isHereditary = isHereditary;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	public Object getDefaultValue() {
		return defaultValue;
	}

	public final Object getOption() {
		return option;
	}

	public Class<?> getType() {
		return type;
	}

	public boolean isHereditary() {
		return isHereditary;
	}

	public boolean isTag() {
		return isTag;
	}
	
	public Object getValue(Map<?, ?> options) {
		return options.containsKey(option)
			? options.get(option)
			: getDefaultValue();
	}
	
	public boolean isSet(Map<?, ?> options) {
		return options.containsKey(option);
	}
	
	public boolean sameSetting(Map<?, ?> options1, Map<?, ?> options2) {
		return safeEquals(getValue(options1), getValue(options2));
	}
	
	public void inherit(Map<?, ?> parentOptions,
			Map<Object, Object> childOptions, boolean force) {

		if ((force || isHereditary()) && !isSet(childOptions)) {
			childOptions.put(option, getValue(parentOptions));
		}
	}
	
	protected boolean safeEquals(Object a, Object b) {
		return (a == null)
			? b == null
			: a.equals(b);
	}
	
	protected Class<?> safeClass(Object o) {
		return (o == null)
			? Void.class
			: o.getClass();
	}
	
	/**
	 * Creates a new transaction option meta-data for an heritary, non-tag,
	 * boolean-valued option.
	 * 
	 * @param option
	 *            the option key
	 * @param defaultValue
	 *            the option's default value
	 * 
	 * @return the option meta-data
	 */
	public static Transaction.OptionMetadata newBoolean(Object option,
			boolean defaultValue) {
		
		return new BasicTransactionOptionMetadata(option, false, true,
			Boolean.class, Boolean.valueOf(defaultValue));
	}
	
	@Override
	public String toString() {
		return "Option[key=" + getOption() //$NON-NLS-1$
			+ ", isTag=" + isTag() //$NON-NLS-1$
			+ ", isHereditary=" + isHereditary() //$NON-NLS-1$
			+ ", type=" + getType().getName() //$NON-NLS-1$
			+ ", default=" + getDefaultValue() + ']'; //$NON-NLS-1$
	}
}
