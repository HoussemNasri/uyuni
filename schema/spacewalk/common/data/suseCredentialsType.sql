--
-- Copyright (c) 2012 Novell
--
-- This software is licensed to you under the GNU General Public License,
-- version 2 (GPLv2). There is NO WARRANTY for this software, express or
-- implied, including the implied warranties of MERCHANTABILITY or FITNESS
-- FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
-- along with this software; if not, see
-- http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
--
-- Red Hat trademarks are not licensed under GPLv2. No permission is
-- granted to use or replicate Red Hat trademarks that are incorporated
-- in this software or its documentation.
--

insert into suseCredentialsType (id, label, name) values
        (sequence_nextval('suse_credtype_id_seq'), 'scc', 'SUSE Customer Center');

insert into suseCredentialsType (id, label, name) values
        (sequence_nextval('suse_credtype_id_seq'), 'vhm', 'Virtual Host Manager');

insert into suseCredentialsType (id, label, name) values
        (sequence_nextval('suse_credtype_id_seq'), 'registrycreds', 'Registry');

insert into suseCredentialsType (id, label, name) values
    (sequence_nextval('suse_credtype_id_seq'), 'cloudrmt', 'Cloud RMT network');

insert into suseCredentialsType (id, label, name) values
    (sequence_nextval('suse_credtype_id_seq'), 'reportcreds', 'Reporting DB Credentials');

insert into suseCredentialsType (id, label, name) values
    (sequence_nextval('suse_credtype_id_seq'), 'rhui', 'Red Hat Update Infrastructure');

commit;
