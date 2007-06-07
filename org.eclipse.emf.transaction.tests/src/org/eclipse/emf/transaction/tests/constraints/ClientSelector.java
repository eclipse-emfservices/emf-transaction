/**
 * <copyright>
 *
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 * $Id: ClientSelector.java,v 1.2 2007/06/07 14:26:16 cdamus Exp $
 */
package org.eclipse.emf.transaction.tests.constraints;

import org.eclipse.emf.transaction.tests.ValidationRollbackTest;
import org.eclipse.emf.validation.model.IClientSelector;

/**
 * Simple flag-based client selector used by the validation test cases.
 *
 * @author Christian W. Damus (cdamus)
 */
public class ClientSelector
	implements IClientSelector {

	public boolean selects(Object object) {
		return ValidationRollbackTest.validationEnabled;
	}

}
