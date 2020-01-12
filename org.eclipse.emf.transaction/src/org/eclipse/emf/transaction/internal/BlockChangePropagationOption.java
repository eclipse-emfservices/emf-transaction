/**
 * Copyright (c) 2008 Zeligsoft Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *   Zeligsoft - Initial API and implementation
 */
package org.eclipse.emf.transaction.internal;

import java.util.Map;

import org.eclipse.emf.transaction.impl.TransactionImpl;
import org.eclipse.emf.transaction.util.BasicTransactionOptionMetadata;


/**
 * Metadata implementation for the non-trivial complexity of the
 * {@link TransactionImpl#BLOCK_CHANGE_PROPAGATION} transaction option.
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.3
 */
public class BlockChangePropagationOption
		extends BasicTransactionOptionMetadata {

	/**
	 * Initializes me.
	 */
	public BlockChangePropagationOption() {
		super(TransactionImpl.BLOCK_CHANGE_PROPAGATION, false, false,
			Boolean.class, Boolean.FALSE);
	}

	@Override
	public void inherit(Map<?, ?> parentOptions,
			Map<Object, Object> childOptions, boolean force) {

        // never inherit this option, even when requested to force
	}

}
