
load 'pipeline_stages.groovy'

"%.bam" * [ call_variants_hc ] +
  combine_gvcfs +
  joint_call +
  [buildVQSR_SNP, buildVQSR_indel] + applyVQSR_SNP + applyVQSR_indel
]
