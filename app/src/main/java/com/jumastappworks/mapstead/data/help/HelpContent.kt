package com.jumastappworks.mapstead.data.help

import com.jumastappworks.mapstead.R

object HelpContent {
    val TOPICS = listOf(
        HelpTopic(
            id = HelpTopicId.GETTING_STARTED,
            titleRes = R.string.help_topic_getting_started,
            summaryRes = R.string.help_summary_getting_started,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_gs_section_1,
                    bodyRes = R.string.help_gs_body_1,
                    steps = listOf(
                        R.string.help_gs_step_1,
                        R.string.help_gs_step_2,
                        R.string.help_gs_step_3,
                        R.string.help_gs_step_4,
                        R.string.help_gs_step_5,
                        R.string.help_gs_step_6,
                        R.string.help_gs_step_7
                    )
                )
            ),
            keywords = listOf(R.string.help_topic_getting_started, R.string.gs_checklist_title),
            category = HelpCategory.BASICS
        ),
        HelpTopic(
            id = HelpTopicId.PROPERTIES,
            titleRes = R.string.help_topic_properties,
            summaryRes = R.string.help_summary_properties,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_prop_section_1,
                    bodyRes = R.string.help_prop_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_prop_section_2,
                    bodyRes = R.string.help_prop_body_2
                )
            ),
            keywords = listOf(R.string.help_topic_properties, R.string.address_line_1_label, R.string.reports_handoff_title),
            category = HelpCategory.PROPERTY_RECORDS
        ),
        HelpTopic(
            id = HelpTopicId.PROPERTY_MAPS,
            titleRes = R.string.help_topic_property_maps,
            summaryRes = R.string.help_summary_property_maps,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_map_section_1,
                    bodyRes = R.string.help_map_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_map_section_2,
                    bodyRes = R.string.help_map_body_2
                )
            ),
            keywords = listOf(R.string.help_topic_property_maps, R.string.basemap, R.string.plans),
            category = HelpCategory.MAPPING
        ),
        HelpTopic(
            id = HelpTopicId.ADD_TO_MAP,
            titleRes = R.string.help_topic_add_to_map,
            summaryRes = R.string.help_summary_add_to_map,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_add_section_1,
                    bodyRes = R.string.help_add_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_add_section_2,
                    bodyRes = R.string.help_add_body_2
                )
            ),
            keywords = listOf(R.string.add_to_map, R.string.add_point, R.string.add_line, R.string.add_area),
            category = HelpCategory.MAPPING
        ),
        HelpTopic(
            id = HelpTopicId.GPS_AND_ACCURACY,
            titleRes = R.string.help_topic_gps,
            summaryRes = R.string.help_summary_gps,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_gps_section_1,
                    bodyRes = R.string.help_gps_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_gps_section_2,
                    bodyRes = R.string.help_gps_body_2
                )
            ),
            keywords = listOf(R.string.help_topic_gps, R.string.gps_quality, R.string.accuracy),
            category = HelpCategory.MAPPING
        ),
        HelpTopic(
            id = HelpTopicId.LAYERS,
            titleRes = R.string.help_topic_layers,
            summaryRes = R.string.help_summary_layers,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_layers_section_1,
                    bodyRes = R.string.help_layers_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_layers_section_2,
                    bodyRes = R.string.help_layers_body_2
                )
            ),
            keywords = listOf(R.string.map_layers, R.string.layer_settings, R.string.starter_layers_title),
            category = HelpCategory.MAPPING
        ),
        HelpTopic(
            id = HelpTopicId.INFRASTRUCTURE,
            titleRes = R.string.help_topic_infrastructure,
            summaryRes = R.string.help_summary_infrastructure,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_infra_section_1,
                    bodyRes = R.string.help_infra_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_infra_section_2,
                    bodyRes = R.string.help_infra_body_2
                )
            ),
            keywords = listOf(R.string.infrastructure, R.string.item_name_label, R.string.equipment_details_header),
            category = HelpCategory.PROPERTY_RECORDS
        ),
        HelpTopic(
            id = HelpTopicId.CONNECTIONS,
            titleRes = R.string.help_topic_connections,
            summaryRes = R.string.help_summary_connections,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_conn_section_1,
                    bodyRes = R.string.help_conn_body_1
                )
            ),
            keywords = listOf(R.string.help_topic_connections, R.string.relationships_header, R.string.linked_item_header),
            category = HelpCategory.PROPERTY_RECORDS
        ),
        HelpTopic(
            id = HelpTopicId.MAINTENANCE,
            titleRes = R.string.help_topic_maintenance,
            summaryRes = R.string.help_summary_maintenance,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_maint_section_1,
                    bodyRes = R.string.help_maint_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_maint_section_2,
                    bodyRes = R.string.help_maint_body_2
                )
            ),
            keywords = listOf(R.string.maintenance, R.string.maintenance_history_section, R.string.reminders_label),
            category = HelpCategory.MAINTENANCE_SAFETY
        ),
        HelpTopic(
            id = HelpTopicId.REMINDERS,
            titleRes = R.string.help_topic_reminders,
            summaryRes = R.string.help_summary_reminders,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_rem_section_1,
                    bodyRes = R.string.help_rem_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_rem_section_2,
                    bodyRes = R.string.help_rem_body_2
                )
            ),
            keywords = listOf(R.string.reminders_label, R.string.due_date_label),
            category = HelpCategory.MAINTENANCE_SAFETY
        ),
        HelpTopic(
            id = HelpTopicId.PHOTOS_AND_FILES,
            titleRes = R.string.help_topic_photos_files,
            summaryRes = R.string.help_summary_photos_files,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_file_section_1,
                    bodyRes = R.string.help_file_body_1
                )
            ),
            keywords = listOf(R.string.files_card_title, R.string.attachments_header, R.string.recent_attachments),
            category = HelpCategory.PROPERTY_RECORDS
        ),
        HelpTopic(
            id = HelpTopicId.EMERGENCY_MODE,
            titleRes = R.string.help_topic_emergency,
            summaryRes = R.string.help_summary_emergency,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_emerg_section_1,
                    bodyRes = R.string.help_emerg_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_emerg_section_2,
                    bodyRes = R.string.help_emerg_body_2
                )
            ),
            keywords = listOf(R.string.emergency_mode, R.string.emergency_item_label, R.string.emergency_instructions_label),
            category = HelpCategory.MAINTENANCE_SAFETY
        ),
        HelpTopic(
            id = HelpTopicId.REPORTS_AND_HANDOFF,
            titleRes = R.string.help_topic_reports,
            summaryRes = R.string.help_summary_reports,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_rep_section_1,
                    bodyRes = R.string.help_rep_body_1
                )
            ),
            keywords = listOf(R.string.reports_handoff_title, R.string.pdf_report_section_title, R.string.handoff_package_section_title),
            category = HelpCategory.PROPERTY_RECORDS
        ),
        HelpTopic(
            id = HelpTopicId.BACKUP_AND_RESTORE,
            titleRes = R.string.help_topic_backup,
            summaryRes = R.string.help_summary_backup,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_back_section_1,
                    bodyRes = R.string.help_back_body_1
                )
            ),
            keywords = listOf(R.string.backup_restore, R.string.safety_backups, R.string.google_drive_backups),
            category = HelpCategory.DATA_APP_INFO
        ),
        HelpTopic(
            id = HelpTopicId.MEASUREMENTS_AND_UNITS,
            titleRes = R.string.help_topic_measurements,
            summaryRes = R.string.help_summary_measurements,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_meas_section_1,
                    bodyRes = R.string.help_meas_body_1
                )
            ),
            keywords = listOf(R.string.display_units, R.string.unit_imperial, R.string.unit_metric),
            category = HelpCategory.DATA_APP_INFO
        ),
        HelpTopic(
            id = HelpTopicId.SAFETY_AND_LIMITATIONS,
            titleRes = R.string.help_topic_safety_limitations,
            summaryRes = R.string.help_summary_safety_limitations,
            sections = listOf(
                HelpSection(
                    headingRes = R.string.help_safe_section_1,
                    bodyRes = R.string.help_safe_body_1
                ),
                HelpSection(
                    headingRes = R.string.help_safe_section_2,
                    bodyRes = R.string.help_safe_body_2
                )
            ),
            keywords = listOf(R.string.safety_limitations, R.string.boundary_disclaimer_title, R.string.gps_limitations_title),
            category = HelpCategory.MAINTENANCE_SAFETY
        )
    )
}
