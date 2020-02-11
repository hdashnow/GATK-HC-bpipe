// Settings
TMP="/tmp"
GATK="gatk"


call_variants_hc = {
  doc "Call single sample HaplotypeCaller to produce a GVCF"
    output.dir="variants"

// set TARGET to appropriate chromosomes?
    transform("bam") to("gvcf.gz") {
        exec """
          $GATK --java-options "-Xmx4g" HaplotypeCaller
            -R $REF
            -I $input.bam
            --dbsnp $DBSNP
            -L $TARGET
            -O $output.gz
            -ERC GVCF
            """
    }
}

combine_gvcfs = {
  doc "Combine GVCF from multiple samples to do joint calling"
    output.dir="variants"

    from ('*.gvcf.gz') produce ('all.gvcf.db'){

    exec """
        $GATK --java-options "-Xmx4g -Xms4g" GenomicsDBImport
          ${inputs.gvcf.withFlag("-V ")}
          --genomicsdb-workspace-path $output.db
    """
    }
}

joint_call = {
  doc "Do joint calling on a multi-sample GVCF"
  output.dir="variants"

  from ('gvcf.db') transform ('vcf.gz'){
    exec """
        $GATK --java-options "-Xmx4g" GenotypeGVCFs
            -R $REF
            -V gendb:$input.db
            -O $output.gz
    ""","joint_call"
  }
}

// Source: https://gatk.broadinstitute.org/hc/en-us/articles/360035531112--How-to-Filter-variants-either-with-VQSR-or-by-hard-filtering
buildVQSR_SNP = {
  doc "Build a VQSR recalibration model to score SNP quality on a multi-sample VCF"

  output.dir="variants"

  from ('vcf.gz') transform ('SNP_recal', 'SNP_tranches'){
    exec """
        $GATK --java-options "-Xmx3g -Xms3g" VariantRecalibrator
            -R $REF
            -V $input.gz
            -O $output.SNP_recal
            --tranches-file $output.SNP_tranches
            -mode SNP
            --max-gaussians 6
            --trust-all-polymorphic
            -tranche 100.0 -tranche 99.95 -tranche 99.9 -tranche 99.8 -tranche 99.6 -tranche 99.5 -tranche 99.4 -tranche 99.3 -tranche 99.0 -tranche 98.0 -tranche 97.0 -tranche 90.0
            -an QD -an MQRankSum -an ReadPosRankSum -an FS -an MQ -an SOR -an DP
            -resource hapmap,known=false,training=true,truth=true,prior=15:hapmap_3.3.hg38.vcf.gz
            -resource omni,known=false,training=true,truth=true,prior=12:1000G_omni2.5.hg38.vcf.gz
            -resource 1000G,known=false,training=true,truth=false,prior=10:1000G_phase1.snps.high_confidence.hg38.vcf.gz
            -resource dbsnp,known=true,training=false,truth=false,prior=7:Homo_sapiens_assembly38.dbsnp138.vcf
    """
  }
}

buildVQSR_indel = {
  doc "Build a VQSR recalibration model to score indel quality on a multi-sample VCF"

  output.dir="variants"

  from ('vcf.gz') transform ('indel_recal', 'indel_tranches'){
    exec """
        $GATK --java-options "-Xmx24g -Xms24g" VariantRecalibrator
            -R $REF
            -V $input.gz
            -O $output.indel_recal
            --tranches-file $output.indel_tranches
            -mode INDEL
            --max-gaussians 4
            --trust-all-polymorphic
            -tranche 100.0 -tranche 99.95 -tranche 99.9 -tranche 99.5 -tranche 99.0 -tranche 97.0 -tranche 96.0 -tranche 95.0 -tranche 94.0 -tranche 93.5 -tranche 93.0 -tranche 92.0 -tranche 91.0 -tranche 90.0
            -an FS -an ReadPosRankSum -an MQRankSum -an QD -an SOR -an DP
            -resource mills,known=false,training=true,truth=true,prior=12:Mills_and_1000G_gold_standard.indels.hg38.vcf.gz
            -resource axiomPoly,known=false,training=true,truth=false,prior=10:Axiom_Exome_Plus.genotypes.all_populations.poly.hg38.vcf.gz
            -resource dbsnp,known=true,training=false,truth=false,prior=2:Homo_sapiens_assembly38.dbsnp138.vcf
    """
  }
}

applyVQSR_SNP = {
  doc "Apply SNP VQSR on a multi-sample VCF"

  output.dir="variants"

  from ('vcf.gz', 'SNP_recal', 'SNP_tranches') filter ('snp_recal.vcf.gz'){

    exec """
        $GATK --java-options "-Xmx5g -Xms5g" ApplyVQSR
            -R $REF
            -V $input.gz
            -O $output.gz
            -mode SNP
            --tranches-file $input.SNP_tranches
            --recal-file $input.SNP_recal
            --truth-sensitivity-filter-level 99.7
            --create-output-variant-index true
    """
  }
}

applyVQSR_indel = {
  doc "Apply indel VQSR on a multi-sample VCF"

  output.dir="variants"

  from ('vcf.gz', 'indel_recal', 'indel_tranches') filter ('indel_recal.vcf.gz'){

    exec """
        $GATK --java-options "-Xmx5g -Xms5g" ApplyVQSR
            -R $REF
            -V $input.gz
            -O $output.gz
            -mode INDEL
            --tranches-file $input.indel_tranches
            --recal-file $input.indel_recal
            --truth-sensitivity-filter-level 99.7
            --create-output-variant-index true
    """
  }
}
