
load 'pipeline_stages.groovy'

"%.bam" * [ call_variants_hc ] +
  combine_gvcfs +
  joint_calling +
]
