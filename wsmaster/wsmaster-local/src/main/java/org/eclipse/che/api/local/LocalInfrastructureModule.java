/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.local;

import com.google.inject.AbstractModule;

import org.eclipse.che.api.local.storage.LocalStorageFactory;
import org.eclipse.che.api.machine.server.spi.SnapshotDao;
import org.eclipse.che.api.ssh.server.spi.SshDao;
import org.eclipse.che.api.user.server.TokenValidator;

public class LocalInfrastructureModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SnapshotDao.class).to(LocalSnapshotDaoImpl.class);
        bind(SshDao.class).to(LocalSshDaoImpl.class);
        bind(TokenValidator.class).to(DummyTokenValidator.class);
        bind(LocalStorageFactory.class);
    }
}
