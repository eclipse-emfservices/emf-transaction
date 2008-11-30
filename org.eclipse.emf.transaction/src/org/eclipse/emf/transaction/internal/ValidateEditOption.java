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
 * $Id: ValidateEditOption.java,v 1.1 2008/11/30 16:38:08 cdamus Exp $
 */

package org.eclipse.emf.transaction.internal;

import java.util.Map;

import org.eclipse.emf.transaction.Transaction;
import org.eclipse.emf.transaction.util.BasicTransactionOptionMetadata;
import org.eclipse.emf.transaction.util.ValidateEditSupport;

/**
 * Metadata implementation for the non-trivial complexity of the
 * {@link Transaction#OPTION_VALIDATE_EDIT} transaction option.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class ValidateEditOption
		extends BasicTransactionOptionMetadata {

	/**
	 * Initializes me.
	 */
	public ValidateEditOption() {
		super(Transaction.OPTION_VALIDATE_EDIT, true, false, Object.class,
			Boolean.FALSE);
	}

	@Override
	public boolean sameSetting(Map<?, ?> options1, Map<?, ?> options2) {

		Object value1 = getValue(options1);
		Object value2 = getValue(options2);
		boolean result = safeEquals(value1, value2);

		if (!result) {
			// they may, yet, be equivalent

			final Class<?> vesd = ValidateEditSupport.Default.class;

			if (value1 instanceof Boolean) {
				if ((Boolean) value1) {
					// TRUE === new
					// ValidateEditSupport.Default
					result = safeClass(value2) == vesd;
				}
			} else if (safeClass(value1) == vesd) {
				result = Boolean.TRUE.equals(value2)
					|| (safeClass(value2) == vesd);
			}
		}

		return result;
	}

}
