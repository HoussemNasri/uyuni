--
-- Copyright (c) 2023 SUSE LLC
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

CREATE TABLE suseOVALDefinition
(
    id              VARCHAR NOT NULL
                       CONSTRAINT suse_oval_definition_id_pk PRIMARY KEY,
    class           VARCHAR NOT NULL,
    title           VARCHAR,
    description     VARCHAR(10000),
    cve_id          NUMERIC
                       REFERENCES rhnCve(id),
    os_family       VARCHAR,
    os_version      VARCHAR,
    criteria_tree   JSON
);

create or replace function create_definition(id_in varchar, class_in varchar, title_in varchar, descr_in varchar, cve_in varchar,
                                  os_family_in varchar, os_version_in varchar, criteria_tree_in varchar) returns void
  language plpgsql
as
$$
declare
    cve_id_val     numeric;
begin

    /* name has to be unique */
    INSERT INTO rhncve(id, name)
    VALUES (nextval('rhn_cve_id_seq'), cve_in)
    ON CONFLICT(name) DO NOTHING;

    SELECT id INTO cve_id_val FROM rhncve WHERE name = cve_in;

    INSERT INTO suseOVALDefinition(id, class, title, description, cve_id, os_family, os_version, criteria_tree)
    VALUES (id_in, class_in, title_in, descr_in, cve_id_val, os_family_in, os_version_in,
            CAST(criteria_tree_in AS json))
    ON CONFLICT(id) DO UPDATE
        SET id            = EXCLUDED.id,
            class         = EXCLUDED.class,
            title         = EXCLUDED.title,
            description   = EXCLUDED.description,
            cve_id        = EXCLUDED.cve_id,
            os_family     = EXCLUDED.os_family,
            os_version    = EXCLUDED.os_version,
            criteria_tree = EXCLUDED.criteria_tree;

end;
$$;


create or replace function add_affected_platform_to_definition(definition_id_in varchar, platform_name_in varchar) returns void
    language plpgsql
as
$$
declare
    platform_id_val   numeric;
begin

    INSERT INTO suseovalplatform(id, cpe)
    VALUES (nextval('suse_oval_platform_id_seq'), platform_name_in)
    ON CONFLICT(cpe) DO NOTHING;

    SELECT id INTO platform_id_val FROM suseovalplatform WHERE cpe = platform_name_in;

    INSERT INTO suseovaldefinitionaffectedplatform(definition_id, platform_id)
    VALUES (definition_id_in, platform_id_val)
    ON CONFLICT ON CONSTRAINT suse_oval_def_affected_plat_uq DO NOTHING;
end;
$$;

CREATE UNIQUE INDEX suse_oval_aff_platform_cpe_uq
    ON suseovalplatform(cpe);