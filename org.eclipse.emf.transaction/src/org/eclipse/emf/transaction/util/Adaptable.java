/**
 * <copyright>
 *
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   IBM - Initial API and implementation
 *
 * </copyright>
 *
 * $Id: Adaptable.java,v 1.2 2007/11/14 18:14:00 cdamus Exp $
 */
package org.eclipse.emf.transaction.util;

/**
 * <p>
 * Interface implemented by transactional editing domains that support
 * dynamic adaptation to optional interfaces.
 * </p><p>
 * As the <tt>Adaptable</tt> interface is, itself, optional, the
 * {@link TransactionUtil} class provides a convenient
 * {@link TransactionUtil#getAdapter(org.eclipse.emf.transaction.TransactionalEditingDomain, Class)}
 * method to attempt to obtain an adapter for any editing domain.
 * </p>
 * 
 * @author Christian W. Damus (cdamus)
 * 
 * @since 1.2
 */
public interface Adaptable {
    /**
     * Obtains an instance of the specified adapter type.
     * 
     * @param <T> the interface for which to get an adapter
     * 
     * @param adapterType the required interface
     * @return an instance of the required interface that adapts me, or
     *    <code>null</code> if I do not supply this interface
     */
    <T> T getAdapter(Class<? extends T> adapterType);
}
